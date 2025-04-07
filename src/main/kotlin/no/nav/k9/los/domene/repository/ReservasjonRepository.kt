package no.nav.k9.los.domene.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

class ReservasjonRepository(
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val dataSource: DataSource
) {
    companion object {
        val RESERVASJON_YTELSE_LOG = LoggerFactory.getLogger("ReservasjonYtelseDebug")
    }
    private val log: Logger = LoggerFactory.getLogger(ReservasjonRepository::class.java)

    suspend fun hentOgFjernInaktiveReservasjoner(saksbehandlersIdent: String): List<Reservasjon> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(ident = saksbehandlersIdent)!!
        if (saksbehandler.reservasjoner.isEmpty()) {
            return emptyList()
        }
        return hentOgFjernInaktiveReservasjoner(saksbehandler.reservasjoner, saksbehandlersIdent)
    }

    fun hentOgFjernInaktiveReservasjoner(reservasjonIder: Set<UUID>, saksbehandlersIdent: String? = null): List<Reservasjon> {
        var fjernede: List<Reservasjon>
        var reservasjoner: List<Reservasjon>

        val tidHente = measureTimeMillis {
            reservasjoner = hentReservasjoner(reservasjonIder)
        }
        val andres = reservasjoner.filter { it.reservertAv != saksbehandlersIdent }
        if (andres.isNotEmpty()) {
            RESERVASJON_YTELSE_LOG.info("inkonsistent data for reservasjoner, ${andres.size} reservasjoner registrert i saksbehandlerobjektet til ${saksbehandlersIdent} med reservertAv av: ${andres.map { it.reservertAv }.distinct()}")
        }
        val tidFjerne = measureTimeMillis {
            fjernede = fjernInaktiveReservasjoner(reservasjoner, saksbehandlersIdent)
        }

        RESERVASJON_YTELSE_LOG.info("henting og fjerning av {} reservasjoner tok {} ms for fjerning og {} ms for henting", reservasjonIder.size, tidFjerne, tidHente)
        return fjernede
    }

    fun hentSelvOmDeIkkeErAktive(reservasjoner: Set<UUID>): List<Reservasjon> {
        return hentReservasjoner(reservasjoner)
    }

    fun hentOppgaveUuidMedAktivReservasjon(reservasjoner: Set<UUID>): Set<UUID> {
        if (reservasjoner.isEmpty()){
            //ikke vits å gjøre noe spørring når alt uansett filtreres bort
            return emptySet()
        }
        val nå = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
        log.info("Filtrerer bort reservasjoner utgått før $nå")
        return using(sessionOf(dataSource)) {
            session -> session.run(
                queryOf(
                    """
                        select (data ::jsonb -> 'reservasjoner' -> -1 ->> 'oppgave') as oppgaveUuid from reservasjon
                         where (data ::jsonb -> 'reservasjoner' -> -1 ->> 'reservertTil') > :reservertTil
                         and id in (${InClauseHjelper.tilParameternavn(reservasjoner, "r")})
                        """
                    ,
                    mapOf("reservertTil" to nå)
                    +
                    InClauseHjelper.parameternavnTilVerdierMap(reservasjoner.map { it.toString() }, "r")
                )
                    .map { row ->
                        UUID.fromString(row.string("oppgaveUuid"))
                    }.asList
            ).toSet()
        }
    }

    private fun hentReservasjoner(set: Set<UUID>): List<Reservasjon> {
        if (set.isEmpty()){
            return emptyList()
        }
        val json: List<String> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select (data ::jsonb -> 'reservasjoner' -> -1) as data from reservasjon where id in (" + InClauseHjelper.tilParameternavn(set, "r") + ")",
                    InClauseHjelper.parameternavnTilVerdierMap(set.map { it.toString() }, "r")
                )
                    .map { row ->row.string("data")}
                    .asList
            )
        }

        return json.map { s -> LosObjectMapper.instance.readValue(s, Reservasjon::class.java) }.toList()
    }

    private fun fjernInaktivReservasjon(
        reservasjon: Reservasjon,
        oppgaveKøer: List<OppgaveKø>,
        saksbehandlersIdent: String?
    ): Int {
        lagre(reservasjon.oppgave) {
            it!!.reservertTil = null
            it
        }
        saksbehandlerRepository.fjernReservasjon(reservasjon.reservertAv, reservasjon.oppgave)
        if (saksbehandlersIdent != null && reservasjon.reservertAv != saksbehandlersIdent) {
            saksbehandlerRepository.fjernReservasjon(saksbehandlersIdent, reservasjon.oppgave)
        }
        val oppgave = oppgaveRepository.hent(reservasjon.oppgave)


        var fjernetFraAntallKøer = 0
        oppgaveKøer.forEach { oppgaveKø ->
            if (oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                    oppgave,
                    this,
                )
            ) {
                oppgaveKøRepository.lagreInkluderKode6(oppgaveKø.id) {
                    it!!.leggOppgaveTilEllerFjernFraKø(
                        oppgave = oppgave,
                        reservasjonRepository = this,
                    )
                    it
                }
                fjernetFraAntallKøer += 1

            }
        }
        return fjernetFraAntallKøer
    }

    private fun fjernReservasjonPåInaktivOppgave(reservasjon: Reservasjon) {
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

    private fun fjernInaktiveReservasjoner(reservasjoner: List<Reservasjon>, saksbehandlersIdent: String?): List<Reservasjon> {
        val reservasjonPrAktive = reservasjoner.groupBy { it.erAktiv() }
        val inaktive = reservasjonPrAktive[false] ?: emptyList()
        var totalAntallFjerninger = 0
        if (inaktive.isNotEmpty()) {
            val oppgaveKøer = oppgaveKøRepository.hentAlleInkluderKode6()
            val tid = measureTimeMillis {
                inaktive.forEach { reservasjon ->
                    totalAntallFjerninger += fjernInaktivReservasjon(reservasjon, oppgaveKøer, saksbehandlersIdent)

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
        return if (json != null) LosObjectMapper.instance.readValue(json, Reservasjon::class.java) else null
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
        return json != null
    }

    fun lagre(uuid: UUID, refresh: Boolean = false, f: (Reservasjon?) -> Reservasjon): Reservasjon {
        var reservasjon: Reservasjon? = null
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                reservasjon = lagreReservasjon(tx, uuid, refresh, f)
            }
        }
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
