package no.nav.k9.los.domene.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.aksjonspunktbehandling.objectMapper
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.sse.RefreshKlienter.sendOppdaterReserverte
import no.nav.k9.los.tjenester.sse.SseEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class ReservasjonRepository(
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val dataSource: DataSource,
    private val refreshKlienter: Channel<SseEvent>
) {
    private val log: Logger = LoggerFactory.getLogger(ReservasjonRepository::class.java)

    suspend fun hent(saksbehandlersIdent: String): List<Reservasjon> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(ident = saksbehandlersIdent)!!
        if (saksbehandler.reservasjoner.isEmpty()) {
            return emptyList()
        }
        return hent(saksbehandler.reservasjoner)
    }

    suspend fun hent(reservasjoner: Set<UUID>): List<Reservasjon> {
        return fjernReservasjonerSomIkkeLengerErAktive(
            hentReservasjoner(reservasjoner)
        )
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

        return json.map { s -> objectMapper().readValue(s, Reservasjon::class.java) }.toList()
    }

    private suspend fun fjernReservasjonerSomIkkeLengerErAktive(reservasjoner: List<Reservasjon>): List<Reservasjon> {
        reservasjoner.forEach { reservasjon ->

            if (!reservasjon.erAktiv()) {
                lagre(reservasjon.oppgave) {
                    it!!.reservertTil = null
                    it
                }
                saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
                val oppgave = oppgaveRepository.hent(reservasjon.oppgave)
                oppgaveKøRepository.hentIkkeTaHensyn().forEach { oppgaveKø ->
                    if (oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                            oppgave,
                            this,
                            oppgaveRepositoryV2.hentMerknader(reservasjon.oppgave.toString())
                        )) {
                        oppgaveKøRepository.lagreIkkeTaHensyn(oppgaveKø.id) {
                            it!!.leggOppgaveTilEllerFjernFraKø(
                                oppgave = oppgave,
                                reservasjonRepository = this,
                                merknader = oppgaveRepositoryV2.hentMerknader(reservasjon.oppgave.toString())
                            )
                            it
                        }
                    }
                }
            } else {
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
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        return objectMapper().readValue(json!!, Reservasjon::class.java)
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
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        if (json == null) {
            return emptyList()
        }
        return objectMapper().readValue(json)
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
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

        return json != null
    }

    fun lagre(uuid: UUID, refresh: Boolean = false, f: (Reservasjon?) -> Reservasjon): Reservasjon {
        var reservasjon: Reservasjon? = null
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                reservasjon = lagreReservasjon(tx, uuid, refresh, f)
            }
        }
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()

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
    ) : Reservasjon {
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
            f(objectMapper().readValue(run, Reservasjon::class.java))
        } else {
            f(null)
        }
        val json = objectMapper().writeValueAsString(reservasjon)

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
            loggFjerningAvReservasjon(reservasjon, forrigeReservasjon)
            runBlocking { refreshKlienter.sendOppdaterReserverte() }
        }

        return reservasjon
    }

    private fun loggFjerningAvReservasjon(reservasjon: Reservasjon, forrigeReservasjon: String?) {
        if (forrigeReservasjon != null ) {
            val fr = objectMapper().readValue(forrigeReservasjon, Reservasjon::class.java)
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