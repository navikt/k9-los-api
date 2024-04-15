package no.nav.k9.los.domene.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.sse.RefreshKlienter.sendOppdaterReserverte
import no.nav.k9.los.tjenester.sse.SseEvent
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

class ReservasjonRepository(
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val dataSource: DataSource,
    private val refreshKlienter: Channel<SseEvent>
) {
    companion object {
        val RESERVASJON_YTELSE_LOG = LoggerFactory.getLogger("ReservasjonYtelseDebug")
    }
    private val log: Logger = LoggerFactory.getLogger(ReservasjonRepository::class.java)

    suspend fun hent(saksbehandlersIdent: String): List<Reservasjon> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(ident = saksbehandlersIdent)!!
        if (saksbehandler.reservasjoner.isEmpty()) {
            return emptyList()
        }
        return hent(saksbehandler.reservasjoner)
    }

    suspend fun hent(reservasjoner: Set<UUID>): List<Reservasjon> {
        var fjernede: List<Reservasjon>

        val tid = measureTimeMillis {
            fjernede = fjernReservasjonerSomIkkeLengerErAktive2(
                hentReservasjoner(reservasjoner)
            )
        }

        RESERVASJON_YTELSE_LOG.info("henting og fjerning av {} reservasjoner tok {} ms", reservasjoner.size, tid)
        return fjernede
    }

    fun hentSelvOmDeIkkeErAktive(reservasjoner: Set<UUID>): List<Reservasjon> {
        return hentReservasjoner(reservasjoner)
    }

    private fun hentReservasjoner(set: Set<UUID>): List<Reservasjon> {
        val json: List<String> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon \n" +
                            "where id in " + set.joinToString(
                        separator = "\', \'",
                        prefix = "(\'",
                        postfix = "\')"
                    )
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return json.map { s -> LosObjectMapper.instance.readValue(s, Reservasjon::class.java) }.toList()
    }

    private suspend fun fjernInaktivReservasjon(
        reservasjon: Reservasjon,
        oppgaveKøer: List<OppgaveKø>
    ): Int {
        //TODO fjerning av en (eller flere) reservasjon bør skje i én transaksjon, nå er det en transaksjon for hver del-operasjon (bl.a. en for hver kø)
        lagre(reservasjon.oppgave) {
            it!!.reservertTil = null
            it
        }
        saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        val oppgave = oppgaveRepository.hent(reservasjon.oppgave)
        var fjernetFraAntallKøer = 0
        oppgaveKøer.forEach { oppgaveKø ->
            if (oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                    oppgave,
                    this,
                    oppgaveRepositoryV2.hentMerknader(reservasjon.oppgave.toString())
                )
            ) {
                oppgaveKøRepository.lagreIkkeTaHensyn(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(
                        oppgave = oppgave,
                        reservasjonRepository = this,
                        merknader = oppgaveRepositoryV2.hentMerknader(reservasjon.oppgave.toString())
                    )
                    it
                }
                fjernetFraAntallKøer += 1

            }
        }
        return fjernetFraAntallKøer
    }

    private fun fjernReservasjonPåInaktivOppgave(reservasjon: Reservasjon) {
        //TODO fjerning av en reservasjon bør skje i én transaksjon, nå er det en transaksjon for hver del-operasjon
        val oppgave = oppgaveRepository.hentHvis(reservasjon.oppgave)
        if (oppgave != null) {
            if (!oppgave.aktiv) {
                lagre(reservasjon.oppgave) {
                    it!!.reservertTil = null
                    it
                }
                saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
            }
        } else {
            saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        }
    }

    private suspend fun fjernReservasjonerSomIkkeLengerErAktive2(reservasjoner: List<Reservasjon>): List<Reservasjon> {
        val reservasjonPrAktive = reservasjoner.groupBy { it.erAktiv() }
        val inaktive = reservasjonPrAktive[false] ?: emptyList()
        var totalAntallFjerninger = 0
        if (inaktive.isNotEmpty()) {
            val oppgaveKøer = oppgaveKøRepository.hentIkkeTaHensyn()
            val tid = measureTimeMillis {
                inaktive.forEach { reservasjon ->
                    totalAntallFjerninger += fjernInaktivReservasjon(reservasjon, oppgaveKøer)

                }
            }
            RESERVASJON_YTELSE_LOG.info("{} fjerninger av {} inaktive reservasjoner fra potensielle {} køer tok {} ms", totalAntallFjerninger, inaktive.size, oppgaveKøer.size, tid)
        }

        val aktive = reservasjonPrAktive[true] ?: emptyList()
        aktive.forEach { reservasjon ->
            fjernReservasjonPåInaktivOppgave(reservasjon)
        }


        return reservasjoner.filter { it.erAktiv() }
    }

    fun hent(id: UUID): Reservasjon {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon where id = :id",
                    mapOf("id" to id.toString())
                ).map { row ->
                    row.string("data")
                }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return LosObjectMapper.instance.readValue(json!!, Reservasjon::class.java)
    }

    fun hentOptional(id: UUID): Reservasjon? {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon where id = :id",
                    mapOf("id" to id.toString())
                ).map { row ->
                    row.string("data")
                }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return LosObjectMapper.instance.readValue(json!!, Reservasjon::class.java)
    }

    fun hent(id: UUID, tx: TransactionalSession): Reservasjon? {
        val json: String? = tx.run(
            queryOf(
                "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon where id = :id",
                mapOf("id" to id.toString())
            ).map { row ->
                row.string("data")
            }.asSingle
        )
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return if (json == null) null else  LosObjectMapper.instance.readValue(json, Reservasjon::class.java)
    }

    fun hentMedHistorikk(id: UUID): List<Reservasjon> {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select (data ::jsonb -> 'reservasjoner') as data from reservasjon where id = :id",
                    mapOf("id" to id.toString())
                ).map { row ->
                    row.string("data")
                }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        if (json == null) {
            return emptyList()
        }
        return LosObjectMapper.instance.readValue(json)
    }

    fun hentAlleReservasjonUUID(): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select id from reservasjon",
                ).map { row ->
                    UUID.fromString(row.string("id"))
                }.asList
            )
        }
    }

    fun finnes(id: UUID): Boolean {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon where id = :id",
                    mapOf("id" to id.toString())
                ).map { row ->
                    row.string("data")
                }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return json != null
    }

    fun lagre(uuid: UUID, refresh: Boolean = false, f: (Reservasjon?) -> Reservasjon): Reservasjon {
        var reservasjon: Reservasjon? = null
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                reservasjon = lagreReservasjon(tx, uuid, refresh, f)
            }
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return reservasjon!!
    }

    fun lagreFlereReservasjoner(reservasjon: List<Reservasjon>) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                reservasjon.forEach { reservasjon ->
                    lagreReservasjon(tx, reservasjon.oppgave, refresh = true) {
                        reservasjon
                    }
                }
            }
        }
    }

    private fun lagreReservasjon(
        tx: TransactionalSession,
        uuid: UUID,
        refresh: Boolean,
        f: (Reservasjon?) -> Reservasjon
    ): Reservasjon {
        val reservasjon: Reservasjon?
        val run = tx.run(
            queryOf(
                "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon where id = :id for update",
                mapOf("id" to uuid.toString())
            )
                .map { row ->
                    row.string("data")
                }.asSingle
        )
        var forrigeReservasjon: String? = null
        reservasjon = if (!run.isNullOrEmpty()) {
            forrigeReservasjon = run
            f(LosObjectMapper.instance.readValue(run, Reservasjon::class.java))
        } else {
            f(null)
        }
        val json = LosObjectMapper.instance.writeValueAsString(reservasjon)

        tx.run(
            queryOf(
                """
                        insert into reservasjon as k (id, data)
                        values (:id, :dataInitial :: jsonb)
                        on conflict (id) do update
                        set data = jsonb_set(k.data, '{reservasjoner,999999}', :data :: jsonb, true)
                     """, mapOf(
                    "id" to uuid.toString(),
                    "dataInitial" to "{\"reservasjoner\": [$json]}",
                    "data" to json
                )
            ).asUpdate
        )
        if (refresh && forrigeReservasjon != json) {
            val refreshTid = measureTimeMillis {
                loggFjerningAvReservasjon(reservasjon, forrigeReservasjon)
                runBlocking { refreshKlienter.sendOppdaterReserverte() }
            }
            RESERVASJON_YTELSE_LOG.info("refresh av reservasjoner tok {}", refreshTid)
        }

        return reservasjon
    }

    private fun loggFjerningAvReservasjon(reservasjon: Reservasjon, forrigeReservasjon: String?) {
        if (forrigeReservasjon != null) {
            val fr = LosObjectMapper.instance.readValue(forrigeReservasjon, Reservasjon::class.java)
            val nyBegrunnelse = reservasjon.begrunnelse != null && reservasjon.begrunnelse != fr.begrunnelse
            if (!reservasjon.erAktiv() && fr.erAktiv() && reservasjon.reservertAv == fr.reservertAv) {
                log.info("RESERVASJONDEBUG: Fjerner ${reservasjon.reservertAv} oppgave=${reservasjon.oppgave} begrunnelse=$nyBegrunnelse i reservasjonstabellen")
            }
            if (reservasjon.erAktiv() && fr.erAktiv() && reservasjon.reservertAv != fr.reservertAv) {
                log.info("RESERVASJONDEBUG: Endrer fra ${fr.reservertAv} til ${reservasjon.reservertAv} oppgave=${reservasjon.oppgave} begrunnelse=$nyBegrunnelse i reservasjonstabellen")
            }
        }
    }
}
