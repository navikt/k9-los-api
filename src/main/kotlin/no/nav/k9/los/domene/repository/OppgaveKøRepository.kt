package no.nav.k9.los.domene.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.modell.OppgaveIdMedDato
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.tjenester.sse.RefreshKlienter.sendOppdaterTilBehandling
import no.nav.k9.los.tjenester.sse.SseEvent
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class OppgaveKøRepository(
    private val dataSource: DataSource,
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val oppgaveKøOppdatert: Channel<UUID>,
    private val refreshKlienter: Channel<SseEvent>,
    private val oppgaveRefreshChannel: Channel<UUID>,
    private val pepClient: IPepClient
) {
    private val log: Logger = LoggerFactory.getLogger(OppgaveKøRepository::class.java)
    suspend fun hent(): List<OppgaveKø> {
        val skjermet = pepClient.harTilgangTilKode6()
        val json: List<String> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from oppgaveko where skjermet = :skjermet",
                    mapOf("skjermet" to skjermet)
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        return json.map { s -> LosObjectMapper.instance.readValue(s, OppgaveKø::class.java) }.toList()
    }

    fun hentIkkeTaHensyn(): List<OppgaveKø> {
        val json: List<String> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from oppgaveko",
                    mapOf()
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        return json.map { s -> LosObjectMapper.instance.readValue(s, OppgaveKø::class.java) }.toList()
    }

    fun hentKøIdIkkeTaHensyn(): List<UUID> {
        val uuidListe: List<UUID> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select id from oppgaveko",
                    mapOf()
                )
                    .map { row ->
                        UUID.fromString(row.string("id"))
                    }.asList
            )
        }

        return uuidListe
    }

    suspend fun hentOppgavekø(id: UUID, ignorerSkjerming: Boolean = false): OppgaveKø {
        val kø = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from oppgaveko where id = :id",
                    mapOf("id" to id.toString())
                ).map { row ->
                    row.string("data")
                }.asSingle
            )
        }?.let { LosObjectMapper.instance.readValue(it, OppgaveKø::class.java) }
            ?: throw IllegalStateException("Fant ikke oppgavekø med id $id")

        return kø.takeIf { ignorerSkjerming || kø.kode6 == pepClient.harTilgangTilKode6() }
            ?: throw IllegalStateException("Klarte ikke å hente oppgavekø med id $id")
    }

    suspend fun lagre(
        uuid: UUID,
        refresh: Boolean = false,
        f: (OppgaveKø?) -> OppgaveKø
    ) {
        val kode6 = pepClient.harTilgangTilKode6()
        using(sessionOf(dataSource)) { it ->
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from oppgaveko where id = :id and skjermet = :skjermet for update",
                        mapOf("id" to uuid.toString(), "skjermet" to kode6)
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )
                val forrigeOppgavekø: OppgaveKø?
                var oppgaveKø = if (!run.isNullOrEmpty()) {
                    forrigeOppgavekø = LosObjectMapper.instance.readValue(run, OppgaveKø::class.java)
                    f(forrigeOppgavekø)
                } else {
                    f(null)
                }
                oppgaveKø = oppgaveKø.copy(kode6 = kode6)
                //Sorter oppgaver
                oppgaveKø.sistEndret = LocalDate.now()
                oppgaveKø.oppgaverOgDatoer.sortBy { it.dato }
                val json = LosObjectMapper.instance.writeValueAsString(oppgaveKø)
                tx.run(
                    queryOf(
                        """
                        insert into oppgaveko as k (id, data, skjermet)
                        values (:id, :data :: jsonb, :skjermet)
                        on conflict (id) do update
                        set data = :data :: jsonb, skjermet = :skjermet
                     """, mapOf("id" to uuid.toString(), "data" to json, "skjermet" to kode6)
                    ).asUpdate
                )

            }
        }
        if (refresh) {
            refreshKlienter.sendOppdaterTilBehandling(uuid)
        }
    }

    suspend fun leggTilOppgaverTilKø(
        køUUID: UUID,
        oppgaver: List<Oppgave>,
        reservasjonRepository: ReservasjonRepository,
    ) {
        return leggTilOppgaverTilKø(køUUID, oppgaver) { OppgaveKø.erOppgavenReservert(reservasjonRepository, it) }
    }

    suspend fun leggTilOppgaverTilKø(
        køUUID: UUID,
        oppgaver: List<Oppgave>,
        erOppgavenReservertSjekk : (Oppgave) -> Boolean,
    ) {
        var hintRefresh = false
        using(sessionOf(dataSource)) { it ->
            it.transaction { tx ->
                val gammelJson = tx.run(
                    queryOf(
                        "select data from oppgaveko where id = :id  for update",
                        mapOf("id" to køUUID.toString())
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )
                val oppgaveKø = LosObjectMapper.instance.readValue(gammelJson, OppgaveKø::class.java)
                val første20OppgaverSomVar = oppgaveKø.oppgaverOgDatoer.take(20).toList()

                var finnesOppgavekøMedEndring = false
                for (oppgave in oppgaver) {
                    if (oppgaveKø.kode6 == oppgave.kode6) {
                        val oppgavekøHarEndring = oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                            oppgave = oppgave,
                            erOppgavenReservertSjekk = erOppgavenReservertSjekk
                        )

                        if (oppgavekøHarEndring) {
                            finnesOppgavekøMedEndring = true
                        }
                    }
                }
                if (!finnesOppgavekøMedEndring) {
                    return@transaction
                }
                //Sorter oppgaver
                oppgaveKø.oppgaverOgDatoer.sortBy { it.dato }
                hintRefresh = første20OppgaverSomVar != oppgaveKø.oppgaverOgDatoer.take(20).toList()
                oppgaveKø.oppgaverOgDatoer.take(20).forEach { runBlocking { oppgaveRefreshChannel.send(it.id) } }
                tx.run(
                    queryOf(
                        """
                        insert into oppgaveko as k (id, data, skjermet)
                        values (:id, :data :: jsonb, :skjermet)
                        on conflict (id) do update
                        set data = :data :: jsonb
                     """, mapOf("id" to køUUID.toString(),
                            "data" to LosObjectMapper.instance.writeValueAsString(oppgaveKø))
                    ).asUpdate
                )

            }
        }

        if (hintRefresh) {
            refreshKlienter.sendOppdaterTilBehandling(køUUID)
        }
    }

    suspend fun lagreIkkeTaHensyn(
        uuid: UUID,
        f: (OppgaveKø?) -> OppgaveKø
    ) {

        var hintRefresh = false
        using(sessionOf(dataSource)) { it ->
            it.transaction { tx ->
                val gammelJson = tx.run(
                    queryOf(
                        "select data from oppgaveko where id = :id  for update",
                        mapOf("id" to uuid.toString())
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )
                val første20OppgaverSomVar: List<OppgaveIdMedDato>
                val forrigeOppgavekø: OppgaveKø?
                val oppgaveKø = if (!gammelJson.isNullOrEmpty()) {
                    forrigeOppgavekø = LosObjectMapper.instance.readValue(gammelJson, OppgaveKø::class.java)
                    første20OppgaverSomVar = forrigeOppgavekø.oppgaverOgDatoer.take(20)
                    f(forrigeOppgavekø)
                } else {
                    første20OppgaverSomVar = listOf()
                    f(null)
                }
                //Sorter oppgaver
                oppgaveKø.oppgaverOgDatoer.sortBy { it.dato }
                hintRefresh = første20OppgaverSomVar != oppgaveKø.oppgaverOgDatoer.take(20)
                val json = LosObjectMapper.instance.writeValueAsString(oppgaveKø)
                if (json == gammelJson) {
                    log.info("Ingen endring i oppgavekø " + oppgaveKø.navn)
                    return@transaction
                }
                tx.run(
                    queryOf(
                        """
                        insert into oppgaveko as k (id, data, skjermet)
                        values (:id, :data :: jsonb, :skjermet)
                        on conflict (id) do update
                        set data = :data :: jsonb
                     """, mapOf("id" to uuid.toString(), "data" to json)
                    ).asUpdate
                )

            }
        }

        if (hintRefresh) {
            refreshKlienter.sendOppdaterTilBehandling(uuid)
        }
    }

    suspend fun slett(id: UUID) {
        val skjermet = pepClient.harTilgangTilKode6()
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    delete from oppgaveko
                    where id = :id and skjermet = :skjermet
                 """, mapOf("id" to id.toString(), "skjermet" to skjermet)
                    ).asUpdate
                )
            }
        }
    }

    suspend fun oppdaterKøMedOppgaver(uuid: UUID) {
        oppgaveKøOppdatert.send(uuid)
    }

}
