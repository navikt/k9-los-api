package no.nav.k9.los.nyoppgavestyring.reservasjon

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.TILGANG_SAK
import no.nav.k9.los.integrasjon.audit.*
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDato
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val pepClient: IPepClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val auditlogger: Auditlogger,
    private val oppgaveRepository: OppgaveRepository,
) {

    fun auditlogReservert(brukerId: Long, reservertoppgave: Oppgave) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(brukerId)
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
                    CefField(CefFieldName.USER_ID, saksbehandler.brukerIdent),
                    CefField(
                        CefFieldName.BERORT_BRUKER_ID,
                        reservertoppgave.hentVerdi("aktorId")
                    ), //TODO gjøre oppgavetypeagnostisk

                    CefField(CefFieldName.BEHANDLING_VERDI, "behandlingsid"),
                    CefField(CefFieldName.BEHANDLING_LABEL, "Behandling"),
                    CefField(
                        CefFieldName.SAKSNUMMER_VERDI,
                        reservertoppgave.hentVerdi("saksnummer")
                    ), //TODO gjøre oppgavetypeagnostisk
                    CefField(CefFieldName.SAKSNUMMER_LABEL, "Saksnummer"),
                )
            )
        )
    }

    fun taReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        kommentar: String,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            taReservasjon(reservasjonsnøkkel, reserverForId, gyldigFra, gyldigTil, kommentar, tx)
        }
    }

    fun taReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String,
        tx: TransactionalSession
    ): ReservasjonV3 {
        //sjekke tilgang på alle oppgaver tilknyttet nøkkel
        val oppgaverForReservasjonsnøkkel =
            oppgaveRepository.hentAlleOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        if (!sjekkTilganger(oppgaverForReservasjonsnøkkel, reserverForId)) {
            throw ManglerTilgangException("Saksbehandler $reserverForId mangler tilgang til å reservere nøkkel $reservasjonsnøkkel")
        }
        //prøv å ta reservasjon
        val reservasjonTilLagring = ReservasjonV3(
            reservertAv = reserverForId,
            reservasjonsnøkkel = reservasjonsnøkkel,
            gyldigFra = gyldigFra,
            gyldigTil = gyldigTil,
            kommentar = kommentar,
        )
        val reservasjon = reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)

        return reservasjon
    }

    fun forsøkReservasjonOgReturnerAktiv(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String,
        utføresAvId: Long
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            try {
                taReservasjon(reservasjonsnøkkel, reserverForId, kommentar = kommentar, gyldigFra, gyldigTil)
            } catch (e: AlleredeReservertException) {
                val aktivReservasjon =
                    reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                        reservasjonsnøkkel,
                        tx
                    )!!
                if (reserverForId != aktivReservasjon.reservertAv) { // reservert av andre
                    aktivReservasjon
                } else if (aktivReservasjon.gyldigTil < gyldigTil) {
                    reservasjonV3Repository.forlengReservasjon(
                        aktivReservasjon,
                        endretAvBrukerId = utføresAvId,
                        nyTildato = gyldigTil,
                        kommentar = kommentar,
                        tx
                    )
                } else {
                    //allerede reservert lengre enn ønsket
                    // TODO: kort ned reservasjon i stedet? Avklaring neste uke. Sjekke opp mot V1-logikken
                    // Alt 1. - kort ned reservasjon dersom det er innlogget bruker sin reservasjon som endres. Ellers IllegalArgument.
                    // Alt 2. - Alltid feilmelding eller "ikke utført", for så å tvinge kall mot "endre reservasjon()" eller lignenden
                    aktivReservasjon
                }
            }
        }
    }

    fun hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel: String) : ReservasjonV3? {
        return transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
        }
    }

    fun hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel: String, tx: TransactionalSession) : ReservasjonV3? {
        return reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
    }

    fun hentReservasjonerForSaksbehandler(saksbehandlerId: Long): List<ReservasjonV3> {
        return transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandlerId, tx)
        }
    }


    fun annullerReservasjon(reservasjonsnøkkel: String, kommentar: String, annullertAvBrukerId: Long) {
        transactionalManager.transaction { tx ->
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)!!
            reservasjonV3Repository.annullerAktivReservasjonOgLagreEndring(
                aktivReservasjon,
                kommentar,
                annullertAvBrukerId,
                tx
            )
        }
    }

    fun forlengReservasjon(
        reservasjonsnøkkel: String,
        nyTildato: LocalDateTime?,
        utførtAvBrukerId: Long,
        kommentar: String,
    ) : ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon = finnAktivReservasjon(reservasjonsnøkkel, tx)
            reservasjonV3Repository.forlengReservasjon(
                aktivReservasjon = aktivReservasjon!!,
                endretAvBrukerId = utførtAvBrukerId,
                nyTildato = nyTildato ?: aktivReservasjon.gyldigTil.plusHours(24).forskyvReservasjonsDato(),
                kommentar = kommentar,
                tx = tx
            )
        }
    }

    fun overførReservasjon(
        reservasjonsnøkkel: String,
        reserverTil: LocalDateTime,
        tilSaksbehandlerId: Long,
        utførtAvBrukerId: Long,
        kommentar: String,
    ) : ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon =
                finnAktivReservasjon(reservasjonsnøkkel, tx)
            reservasjonV3Repository.overførReservasjon(
                aktivReservasjon = aktivReservasjon,
                saksbehandlerSomSkalHaReservasjonId = tilSaksbehandlerId,
                endretAvBrukerId = utførtAvBrukerId,
                reserverTil = reserverTil,
                kommentar = kommentar,
                tx = tx
            )
        }
    }



    fun endreReservasjon(
        reservasjonsnøkkel: String,
        endretAvBrukerId: Long,
        nyTildato: LocalDateTime?,
        nySaksbehandlerId: Long?,
        kommentar: String?
    ) : ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon = finnAktivReservasjon(reservasjonsnøkkel, tx)

            reservasjonV3Repository.endreReservasjon(
                reservasjonSomSkalEndres = aktivReservasjon,
                endretAvBrukerId = endretAvBrukerId,
                nyTildato = nyTildato,
                nySaksbehandlerId = nySaksbehandlerId,
                kommentar = kommentar,
                tx = tx
                )
        }
    }

    private fun sjekkTilganger(oppgaver: List<Oppgave>, brukerId: Long): Boolean {
        oppgaver.forEach { oppgave ->
            val saksnummer = oppgave.hentVerdi("saksnummer") //TODO gjøre oppgavetypeagnostisk
            if (saksnummer != null) { //TODO: Oppgaver uten saksnummer?
                val harTilgang = runBlocking {
                    pepClient.harTilgangTilÅReservereOppgave(
                        oppgave,
                        saksbehandlerRepository.finnSaksbehandlerMedId(brukerId)
                    )
                }
                if (!harTilgang) {
                    return false
                }
            }
        }
        return true
    }

    private fun finnAktivReservasjon(
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val aktivReservasjon =
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
                ?: throw FinnerIkkeDataException("Fant ikke aktiv reservasjon for angitt reservasjonsnøkkel: $reservasjonsnøkkel") //TODO: Lov å logge/vise reservasjonsnøkkel?
        return aktivReservasjon
    }
}