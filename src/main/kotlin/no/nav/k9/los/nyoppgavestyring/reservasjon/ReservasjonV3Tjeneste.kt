package no.nav.k9.los.nyoppgavestyring.reservasjon

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.TILGANG_SAK
import no.nav.k9.los.integrasjon.audit.*
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDato
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepository,
    private val pepClient: IPepClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val auditlogger: Auditlogger,
) {
    fun taReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long
    ): ReservasjonV3? {
        val oppgavekø = transactionalManager.transaction { tx ->
            oppgaveKoRepository.hent(oppgaveKoId)
        }
        val kandidatOppgaver = oppgaveQueryService.queryForOppgaveId(oppgavekø.oppgaveQuery)

        return transactionalManager.transaction { tx ->
            finnOppgaveFraKø(kandidatOppgaver, tx, innloggetBrukerId)
        }

    }

    private fun finnOppgaveFraKø(
        kandidatoppgaver: List<Long>,
        tx: TransactionalSession,
        innloggetBrukerId: Long
    ) : ReservasjonV3? {
        for (kandidatoppgaveId in kandidatoppgaver) {
            val kandidatoppgave = oppgaveRepository.hentOppgaveForId(tx, kandidatoppgaveId)
            //reservert allerede?
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                    kandidatoppgave.reservasjonsnøkkel,
                    tx
                )
            if (aktivReservasjon != null) {
                continue
            }

            //sjekke tilgang på alle oppgaver tilknyttet nøkkel
            val oppgaverForReservasjonsnøkkel =
                oppgaveRepository.hentAlleOppgaverForReservasjonsnøkkel(tx, kandidatoppgave.reservasjonsnøkkel)
            val harTilgangTilAlleOppgaverForReservasjonsnøkkel = sjekkTilganger(oppgaverForReservasjonsnøkkel, innloggetBrukerId)
            if (!harTilgangTilAlleOppgaverForReservasjonsnøkkel) { //TODO: Diskutere og implementere lockout for totrinnskontroll
                continue
            }

            //try catch reservasjon
            try {
                val reservasjon = reservasjonV3Repository.lagreReservasjon(
                    ReservasjonV3(
                        saksbehandlerId = innloggetBrukerId,
                        reservasjonsnøkkel = kandidatoppgave.reservasjonsnøkkel,
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato()
                    ),
                    tx
                )
                auditlogReservert(innloggetBrukerId, kandidatoppgave)
                return reservasjon
            } catch (e: AlleredeReservertException) {
                continue
            }
        }
        return null
    }

    fun auditlogReservert(brukerId: Long, reservertoppgave: Oppgave) {
        saksbehandlerRepository.finnSaksbehandlerMedId(brukerId)
        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = "INFO"
                ), fields = setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, "read"),
                    CefField(CefFieldName.ABAC_RESOURCE_TYPE, TILGANG_SAK),
                    CefField(CefFieldName.ABAC_ACTION, "read"),
                    CefField(CefFieldName.USER_ID, brukerId),
                    CefField(CefFieldName.BERORT_BRUKER_ID, reservertoppgave.hentVerdi("aktorId")), //TODO gjøre oppgavetypeagnostisk

                    CefField(CefFieldName.BEHANDLING_VERDI, "behandlingsid"),
                    CefField(CefFieldName.BEHANDLING_LABEL, "Behandling"),
                    CefField(CefFieldName.SAKSNUMMER_VERDI, reservertoppgave.hentVerdi("saksnummer")), //TODO gjøre oppgavetypeagnostisk
                    CefField(CefFieldName.SAKSNUMMER_LABEL, "Saksnummer"),
                )
            )
        )
    }


    fun taReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        utføresAvId: Long
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            sjekkOgHåndterEksisterendeReservasjon(
                reservasjonsnøkkel,
                reserverForId,
                gyldigFra,
                gyldigTil,
                utføresAvId,
                tx
            )
        }
    }

    private fun sjekkOgHåndterEksisterendeReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        utføresAvId: Long,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val aktivReservasjon =
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                reservasjonsnøkkel,
                tx
            )

        if (aktivReservasjon == null) {
            val reservasjonTilLagring = ReservasjonV3(
                reservertAv = reserverForId,
                reservasjonsnøkkel = reservasjonsnøkkel,
                gyldigFra = gyldigFra,
                gyldigTil = gyldigTil,
            )
            return reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)
        }

        if (reserverForId != aktivReservasjon.reservertAv) { // reservert av andre
            return aktivReservasjon
        }

        if (aktivReservasjon.gyldigTil < gyldigTil) {
            return reservasjonV3Repository.forlengReservasjon(
                aktivReservasjon,
                endretAv = utføresAvId,
                nyTildato = gyldigTil,
                tx
            )
        }

        //allerede reservert lengre enn ønsket
        // TODO: kort ned reservasjon i stedet? Avklaring neste uke. Sjekke opp mot V1-logikken
        // TODO: Alt 1. - kort ned reservasjon dersom det er innlogget bruker sin reservasjon som endres. Ellers IllegalArgument.
        // TODO: Alt 2. - Alltid feilmelding eller "ikke utført", for så å tvinge kall mot "endre reservasjon()" eller lignende
        return aktivReservasjon
    }

    fun hentReservasjonerForSaksbehandler(saksbehandlerId: Long): List<ReservasjonV3> {
        return transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandlerId, tx)
        }
    }


    fun annullerReservasjon(reservasjonsnøkkel: String, annullertAvBrukerId: Long) {
        transactionalManager.transaction { tx ->
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)!!
            reservasjonV3Repository.annullerAktivReservasjonOgLagreEndring(
                aktivReservasjon,
                annullertAvBrukerId,
                tx
            )
        }
    }

    fun overførReservasjon(
        reservasjonsnøkkel: String,
        reserverTil: LocalDateTime,
        tilSaksbehandlerId: Long,
        utførtAvBrukerId: Long
    ) {
        transactionalManager.transaction { tx ->
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)!!
            reservasjonV3Repository.overførReservasjon(
                aktivReservasjon = aktivReservasjon,
                saksbehandlerSomSkalHaReservasjonId = tilSaksbehandlerId,
                endretAvBrukerId = utførtAvBrukerId,
                reserverTil = reserverTil,
                tx
            )
        }
    }

    private fun sjekkTilganger(oppgaver: List<Oppgave>, brukerId: Long): Boolean {
        oppgaver.forEach { oppgave ->
            val saksnummer = oppgave.hentVerdi("saksnummer") //TODO gjøre oppgavetypeagnostisk
            if (saksnummer != null) { //TODO: Oppgaver uten saksnummer?
                val harTilgang = runBlocking {
                    pepClient.harTilgangTilÅReservereOppgave(oppgave, saksbehandlerRepository.finnSaksbehandlerMedId(brukerId))
                }
                if (!harTilgang) {
                    return false
                }
            }
        }
        return true
    }
}