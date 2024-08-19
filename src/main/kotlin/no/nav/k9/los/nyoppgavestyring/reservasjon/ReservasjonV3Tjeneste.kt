package no.nav.k9.los.nyoppgavestyring.reservasjon

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.Auditlogging
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.TILGANG_SAK
import no.nav.k9.los.integrasjon.audit.*
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.ReservasjonAnnullert
import no.nav.k9.los.nyoppgavestyring.ko.ReservasjonEndret
import no.nav.k9.los.nyoppgavestyring.ko.ReservasjonTatt
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.utils.leggTilDagerHoppOverHelg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val pepClient: IPepClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val auditlogger: Auditlogger,
    private val oppgaveV1Repository: no.nav.k9.los.domene.repository.OppgaveRepository,
    private val oppgaveV3Repository: OppgaveRepository,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger("ReservasjonV3Tjeneste")
    }

    fun auditlogReservert(brukerId: Long, reservertoppgave: Oppgave) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(brukerId)!!
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
        utføresAvId: Long,
        kommentar: String?,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            taReservasjon(reservasjonsnøkkel, reserverForId, utføresAvId, gyldigFra, gyldigTil, kommentar, tx)
        }
    }

    fun taReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        utføresAvId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String?,
        tx: TransactionalSession
    ): ReservasjonV3 {
        //sjekke tilgang på alle oppgaver tilknyttet nøkkel
        val oppgaverForReservasjonsnøkkel =
            oppgaveV3Repository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        if (!sjekkTilganger(oppgaverForReservasjonsnøkkel, reserverForId)) {
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(reserverForId)!!
            throw ManglerTilgangException("Saksbehandler ${saksbehandler.navn} mangler tilgang til å reservere nøkkel $reservasjonsnøkkel")
        }
        //prøv å ta reservasjon
        val reservasjonTilLagring = ReservasjonV3(
            reservertAv = reserverForId,
            reservasjonsnøkkel = reservasjonsnøkkel,
            gyldigFra = gyldigFra,
            gyldigTil = gyldigTil,
            kommentar = kommentar,
            endretAv = null
        )
        val reservasjon = reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)
        log.info("taReservasjon: Ny reservasjon $reservasjon, utført av $utføresAvId, for saksbehandler $reserverForId")
        runBlocking {
            køpåvirkendeHendelseChannel.send(ReservasjonTatt(reservasjonsnøkkel = reservasjonsnøkkel))
        }
        return reservasjon
    }

    fun forsøkReservasjonOgReturnerAktiv(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String?,
        utføresAvId: Long,
        tx: TransactionalSession
    ): ReservasjonV3 {
        return try {
            val nå = LocalDateTime.now()
            check(gyldigFra <= nå) { "Gyldig fra er ikke før nå, gyldigfra=${gyldigFra} gyldigTil=${gyldigTil}" }
            check(gyldigTil > nå) { "Gyldig til er ikke etter nå, gyldigfra=${gyldigFra} gyldigTil=${gyldigTil}" }
            taReservasjon(reservasjonsnøkkel, reserverForId, utføresAvId, kommentar = kommentar, gyldigFra, gyldigTil)
        } catch (e: AlleredeReservertException) {
            val aktivReservasjon = reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                reservasjonsnøkkel,
                tx
            )!!

            if (reserverForId != aktivReservasjon.reservertAv) { // reservert av andre
                log.info("ForsøkReservasjonOgReturnerAktiv: AktivReservasjon ${aktivReservasjon} allerede reservert av annen saksbehandler. Utført av $utføresAvId, forsøkt reservert for $reserverForId")
                aktivReservasjon
            } else if (aktivReservasjon.gyldigTil < gyldigTil) {
                log.info("ForsøkReservasjonOgReturnerAktiv: Sb $reserverForId har allerede reservasjonen ${aktivReservasjon}. Forlenger. Utført av $utføresAvId.")
                reservasjonV3Repository.forlengReservasjon(
                    aktivReservasjon,
                    endretAvBrukerId = utføresAvId,
                    nyTildato = gyldigTil,
                    kommentar = kommentar ?: aktivReservasjon.kommentar,
                    tx
                )
            } else {
                log.info("ForsøkReservasjonOgReturnerAktiv: Sb $reserverForId har allerede reservasjonen med id $aktivReservasjon")

                //allerede reservert lengre enn ønsket
                // TODO: kort ned reservasjon i stedet? Avklaring neste uke. Sjekke opp mot V1-logikken
                // Alt 1. - kort ned reservasjon dersom det er innlogget bruker sin reservasjon som endres. Ellers IllegalArgument.
                // Alt 2. - Alltid feilmelding eller "ikke utført", for så å tvinge kall mot "endre reservasjon()" eller lignenden
                aktivReservasjon
            }
        }

    }

    fun hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel: String): ReservasjonV3? {
        return transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
        }
    }

    fun hentAktivReservasjonForReservasjonsnøkkel(
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): ReservasjonV3? {
        return reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
    }

    fun hentReservasjonerForSaksbehandler(saksbehandlerId: Long): List<ReservasjonV3MedOppgaver> {
        return transactionalManager.transaction { tx ->
            val reservasjoner =
                reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandlerId, tx)

            reservasjoner.map { reservasjon ->
                reservasjonV3MedOppgaver(reservasjon, tx)
            }

        }
    }


    fun reservasjonV3MedOppgaver(
        reservasjon: ReservasjonV3,
        tx: TransactionalSession
    ): ReservasjonV3MedOppgaver {
        val oppgaveV1 = hentV1OppgaveFraReservasjon(reservasjon)
        return if (oppgaveV1 != null) {
            ReservasjonV3MedOppgaver(reservasjon, emptyList(), oppgaveV1)
        } else {
            ReservasjonV3MedOppgaver(
                reservasjon,
                oppgaveV3Repository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjon.reservasjonsnøkkel),
                null
            )
        }
    }

    fun hentV1OppgaveFraReservasjon(
        reservasjon: ReservasjonV3
    ): no.nav.k9.los.domene.lager.oppgave.Oppgave? {
        if (reservasjon.reservasjonsnøkkel.startsWith("legacy_")) {
            return oppgaveV1Repository.hent(UUID.fromString(reservasjon.reservasjonsnøkkel.substring(7)))
        } else {
            return null
        }
    }

    fun annullerReservasjonHvisFinnes(
        reservasjonsnøkkel: String,
        kommentar: String?,
        annullertAvBrukerId: Long?,
        tx: TransactionalSession
    ): Boolean {
        val aktivReservasjon = reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
        log.info(
            "Annullerer v3-reservasjon ${aktivReservasjon}, annulleringsforespørsel av type ${
                Reservasjonsnøkkel(
                    reservasjonsnøkkel
                )
            }"
        )

        aktivReservasjon?.let {
            reservasjonV3Repository.annullerAktivReservasjonOgLagreEndring(
                aktivReservasjon,
                kommentar,
                annullertAvBrukerId,
                tx
            )
            runBlocking {
                køpåvirkendeHendelseChannel.send(ReservasjonAnnullert(reservasjonsnøkkel = reservasjonsnøkkel))
            }
            return true
        }
        return false
    }


    fun annullerReservasjonHvisFinnes(
        reservasjonsnøkkel: String,
        kommentar: String?,
        annullertAvBrukerId: Long?
    ): Boolean {
        return transactionalManager.transaction { tx ->
            annullerReservasjonHvisFinnes(reservasjonsnøkkel, kommentar, annullertAvBrukerId, tx)
        }
    }

    fun forlengReservasjon(
        reservasjonsnøkkel: String,
        nyTildato: LocalDateTime?,
        utførtAvBrukerId: Long,
        kommentar: String?,
    ): ReservasjonV3MedOppgaver {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon = finnAktivReservasjon(reservasjonsnøkkel, tx)
            val nyReservasjon = reservasjonV3Repository.forlengReservasjon(
                aktivReservasjon = aktivReservasjon,
                endretAvBrukerId = utførtAvBrukerId,
                nyTildato = nyTildato ?: aktivReservasjon.gyldigTil.leggTilDagerHoppOverHelg(1),
                kommentar = kommentar ?: aktivReservasjon.kommentar,
                tx = tx
            )

            reservasjonV3MedOppgaver(nyReservasjon, tx)
        }
    }

    fun overførReservasjon(
        reservasjonsnøkkel: String,
        reserverTil: LocalDateTime,
        tilSaksbehandlerId: Long,
        utførtAvBrukerId: Long,
        kommentar: String,
    ): ReservasjonV3MedOppgaver {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon = finnAktivReservasjon(reservasjonsnøkkel, tx)
            val nyReservasjon = reservasjonV3Repository.overførReservasjon(
                aktivReservasjon = aktivReservasjon,
                saksbehandlerSomSkalHaReservasjonId = tilSaksbehandlerId,
                endretAvBrukerId = utførtAvBrukerId,
                reserverTil = reserverTil,
                kommentar = kommentar,
                tx = tx
            )
            reservasjonV3MedOppgaver(nyReservasjon, tx)
        }
    }

    fun endreReservasjon(
        reservasjonsnøkkel: String,
        endretAvBrukerId: Long,
        nyTildato: LocalDateTime?,
        nySaksbehandlerId: Long?,
        kommentar: String?
    ): ReservasjonV3MedOppgaver {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon = finnAktivReservasjon(reservasjonsnøkkel, tx)

            val nyReservasjon = reservasjonV3Repository.endreReservasjon(
                reservasjonSomSkalEndres = aktivReservasjon,
                endretAvBrukerId = endretAvBrukerId,
                nyTildato = nyTildato,
                nySaksbehandlerId = nySaksbehandlerId,
                kommentar = kommentar,
                tx = tx
            )
            runBlocking {
                køpåvirkendeHendelseChannel.send(ReservasjonEndret(reservasjonsnøkkel = reservasjonsnøkkel))
            }
            reservasjonV3MedOppgaver(nyReservasjon, tx)
        }
    }

    fun hentAlleAktiveReservasjoner(): List<ReservasjonV3MedOppgaver> {
        return transactionalManager.transaction { tx ->
            val aktiveReservasjoner = reservasjonV3Repository.hentAlleAktiveReservasjoner(tx)
            aktiveReservasjoner.map { reservasjon ->
                reservasjonV3MedOppgaver(reservasjon, tx)
            }
        }
    }

    private fun sjekkTilganger(
        oppgaver: List<Oppgave>,
        brukerIdSomSkalHaReservasjon: Long
    ): Boolean {
        return oppgaver.all { oppgave ->
            if (beslutterErSaksbehandler(
                    oppgave,
                    brukerIdSomSkalHaReservasjon
                )
            ) throw ManglerTilgangException("Saksbehandler kan ikke være beslutter på egen behandling")

            runBlocking { pepClient.harTilgangTilOppgaveV3(oppgave, Action.reserver, Auditlogging.IKKE_LOGG) }
        }
    }

    private fun beslutterErSaksbehandler(
        oppgave: Oppgave,
        brukerIdSomSkalHaReservasjon: Long
    ): Boolean {
        val hosBeslutter =
            oppgave.hentVerdi("liggerHosBeslutter")?.toBoolean() ?: false //TODO gjøre oppgavetypeagnostisk
        if (!hosBeslutter) return false
        val ansvarligSaksbehandlerIdent = oppgave.hentVerdi("ansvarligSaksbehandler") //TODO gjøre oppgavetypeagnostisk
            ?: throw IllegalStateException("Kan ikke beslutte på oppgave uten ansvarlig saksbehandler")
        val saksbehandlerIdentSomSkalHaReservasjon =
            saksbehandlerRepository.finnSaksbehandlerMedId(brukerIdSomSkalHaReservasjon)!!.brukerIdent

        return ansvarligSaksbehandlerIdent == saksbehandlerIdentSomSkalHaReservasjon
    }

    private fun finnAktivReservasjon(
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val aktivReservasjon =
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
                ?: throw FinnerIkkeDataException(
                    "Fant ikke aktiv reservasjon for angitt reservasjonsnøkkel: ${
                        Reservasjonsnøkkel(
                            reservasjonsnøkkel
                        )
                    }"
                )
        return aktivReservasjon
    }
}