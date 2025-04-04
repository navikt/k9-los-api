package no.nav.k9.los.nyoppgavestyring.reservasjon

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Auditlogging
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.ReservasjonAnnullert
import no.nav.k9.los.nyoppgavestyring.ko.ReservasjonEndret
import no.nav.k9.los.nyoppgavestyring.ko.ReservasjonTatt
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.leggTilDagerHoppOverHelg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val pepClient: IPepClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveV1Repository: no.nav.k9.los.domene.repository.OppgaveRepository,
    private val oppgaveV3Repository: OppgaveRepository,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger("ReservasjonV3Tjeneste")
    }

    fun forsøkReservasjonOgReturnerAktivMenSjekkLegacyFørst(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String?,
        utføresAvId: Long
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            forsøkReservasjonOgReturnerAktivMenSjekkLegacyFørst(
                reservasjonsnøkkel,
                reserverForId,
                gyldigFra,
                gyldigTil,
                kommentar,
                utføresAvId,
                tx
            )
        }
    }

    fun forsøkReservasjonOgReturnerAktivMenSjekkLegacyFørst(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String?,
        utføresAvId: Long,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val legacyreservasjon = seEtterLegacyreservasjon(reservasjonsnøkkel, tx)
        if (legacyreservasjon != null) {
            return legacyreservasjon
        }

        return forsøkReservasjonOgReturnerAktiv(
            reservasjonsnøkkel,
            reserverForId,
            gyldigFra,
            gyldigTil,
            kommentar,
            utføresAvId,
            tx
        )
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
            if (gyldigTil < nå) {
                throw ReservasjonUtløptException("Gyldig til er ikke etter nå, gyldigfra=${gyldigFra}, gyldigTil=${gyldigTil}")
            }
            val reservasjon = taReservasjon(
                reservasjonsnøkkel = reservasjonsnøkkel,
                reserverForId = reserverForId,
                utføresAvId = utføresAvId,
                kommentar = kommentar,
                gyldigFra = gyldigFra,
                gyldigTil = gyldigTil,
                tx = tx,
            )
            log.info("taReservasjon: Ny reservasjon $reservasjon, utført av $utføresAvId, for saksbehandler $reserverForId")
            runBlocking {
                køpåvirkendeHendelseChannel.send(ReservasjonTatt(reservasjonsnøkkel = reservasjonsnøkkel))
            }
            reservasjon
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

    fun taReservasjonMenSjekkLegacyFørst(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        utføresAvId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        kommentar: String?,
        tx: TransactionalSession
    ): ReservasjonV3 {
        seEtterLegacyreservasjon(reservasjonsnøkkel, tx)?.let { return it }
        return taReservasjon(reservasjonsnøkkel, reserverForId, utføresAvId, gyldigFra, gyldigTil, kommentar, tx)
    }

    private fun taReservasjon(
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

    fun hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel: String): ReservasjonV3? {
        return transactionalManager.transaction { tx ->
            hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
        }
    }

    fun hentAktivReservasjonForReservasjonsnøkkel(
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): ReservasjonV3? {
        val legacyreservasjon = seEtterLegacyreservasjon(reservasjonsnøkkel, tx)
        if (legacyreservasjon != null) {
            return legacyreservasjon
        }

        return reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
    }

    private fun seEtterLegacyreservasjon(reservasjonsnøkkel: String, tx: TransactionalSession): ReservasjonV3? {
        if (!reservasjonsnøkkel.startsWith("legacy_")) {
            //konvertere reservasjonsnøkkel til legacy_eksternId
            val oppgaver =
                oppgaveV3Repository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
            //sjekke om det finnes en legacy-reservasjon. Kan fjernes etter konvertering
            if (oppgaver.isNotEmpty()) {
                val legacyReservasjon =
                    hentAktivReservasjonForReservasjonsnøkkel("legacy_" + oppgaver[0].eksternId, tx)
                return legacyReservasjon
            }
        }
        return null
    }

    fun hentReservasjonerForSaksbehandler(saksbehandlerId: Long): List<ReservasjonV3MedOppgaver> {
        return transactionalManager.transaction { tx ->
            val reservasjoner =
                reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandlerId, tx)

            reservasjoner.map { reservasjon ->
                finnOppgaverFor(reservasjon, tx)
            }

        }
    }

    fun finnOppgaverFor(
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

            finnOppgaverFor(nyReservasjon, tx)
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
            finnOppgaverFor(nyReservasjon, tx)
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
            finnOppgaverFor(nyReservasjon, tx)
        }
    }

    fun hentAlleAktiveReservasjoner(): List<ReservasjonV3MedOppgaver> {
        return transactionalManager.transaction { tx ->
            val aktiveReservasjoner = reservasjonV3Repository.hentAlleAktiveReservasjoner(tx)
            aktiveReservasjoner.map { reservasjon ->
                finnOppgaverFor(reservasjon, tx)
            }
        }
    }

    private fun sjekkTilganger(
        oppgaver: List<Oppgave>,
        brukerIdSomSkalHaReservasjon: Long
    ): Boolean {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(brukerIdSomSkalHaReservasjon)!!
        return oppgaver.all { oppgave ->
            if (beslutterErSaksbehandler(
                    oppgave,
                    saksbehandler
                )
            ) throw ManglerTilgangException("Saksbehandler kan ikke være beslutter på egen behandling")

            pepClient.harTilgangTilOppgaveV3(oppgave, saksbehandler, Action.reserver, Auditlogging.IKKE_LOGG)
        }
    }

    private fun beslutterErSaksbehandler(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler
    ): Boolean {
        val hosBeslutter =
            oppgave.hentVerdi("liggerHosBeslutter")?.toBoolean() ?: false //TODO gjøre oppgavetypeagnostisk
        if (!hosBeslutter) return false
        val ansvarligSaksbehandlerIdent = oppgave.hentVerdi("ansvarligSaksbehandler") //TODO gjøre oppgavetypeagnostisk
            ?: throw IllegalStateException("Kan ikke beslutte på oppgave uten ansvarlig saksbehandler")
        val saksbehandlerIdentSomSkalHaReservasjon = saksbehandler.brukerIdent

        return ansvarligSaksbehandlerIdent == saksbehandlerIdentSomSkalHaReservasjon
    }

    fun finnAktivReservasjon(
        reservasjonsnøkkel: String,
    ): ReservasjonV3? {
        return transactionalManager.transaction { tx ->
            val legacyreservasjon = seEtterLegacyreservasjon(reservasjonsnøkkel, tx)
            if (legacyreservasjon != null) {
                legacyreservasjon
            } else {
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)
            }
        }
    }

    private fun finnAktivReservasjon(
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): ReservasjonV3 {
        //konvertere reservasjonsnøkkel til legacy_eksternId
        val oppgaver =
            oppgaveV3Repository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkkel)
        if (oppgaver.isNotEmpty()) {
            //sjekke om det finnes en legacy-reservasjon. Kan fjernes etter konvertering
            val legacyReservasjon =
                hentAktivReservasjonForReservasjonsnøkkel("legacy_" + oppgaver[0].eksternId, tx)
            if (legacyReservasjon != null) {
                return legacyReservasjon
            }
        }

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