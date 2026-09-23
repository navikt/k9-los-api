package no.nav.k9.los.reservasjon

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.ko.KøpåvirkendeHendelse
import no.nav.k9.los.ko.ReservasjonAnnullert
import no.nav.k9.los.ko.ReservasjonEndret
import no.nav.k9.los.ko.ReservasjonTatt
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.ReservasjonsnøkkelOppgaveOppslag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val pepClient: IPepClient,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonsnøkkelOppgaveOppslag: ReservasjonsnøkkelOppgaveOppslag,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger("ReservasjonV3Tjeneste")
    }

    suspend fun forsøkReservasjonOgReturnerAktiv(
        område: Områder,
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
                område = område,
                reservasjonsnøkkel = reservasjonsnøkkel,
                reserverForId = reserverForId,
                utføresAvId = utføresAvId,
                kommentar = kommentar,
                gyldigFra = gyldigFra,
                gyldigTil = gyldigTil,
                tx = tx,
            )
            log.info("taReservasjon: Ny reservasjon $reservasjon, utført av $utføresAvId, for saksbehandler $reserverForId")
            køpåvirkendeHendelseChannel.send(ReservasjonTatt(område = område, reservasjonsnøkkel = reservasjonsnøkkel))
            reservasjon
        } catch (e: AlleredeReservertException) {
            val aktivReservasjon = reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                område,
                reservasjonsnøkkel,
                tx
            )!!

            if (reserverForId != aktivReservasjon.reservertAv) { // reservert av andre
                log.info("ForsøkReservasjonOgReturnerAktiv: AktivReservasjon ${aktivReservasjon} allerede reservert av annen saksbehandler. Utført av $utføresAvId, forsøkt reservert for $reserverForId")
                aktivReservasjon
            } else if (aktivReservasjon.gyldigTil < gyldigTil) {
                log.info("ForsøkReservasjonOgReturnerAktiv: Sb $reserverForId har allerede reservasjonen ${aktivReservasjon}. Forlenger. Utført av $utføresAvId.")
                reservasjonV3Repository.forlengReservasjon(
                    område,
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

    suspend fun taReservasjon(
        område: Områder,
        reservasjonsnøkkel: String,
        reserverForId: Long,
        utføresAvId: Long,
        kommentar: String?,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime
    ): ReservasjonV3 {
        return transactionalManager.transactionSuspend { tx ->
            taReservasjon(område, reservasjonsnøkkel, reserverForId, utføresAvId, gyldigFra, gyldigTil, kommentar, tx)
        }
    }

    suspend fun taReservasjon(
        område: Områder,
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
            reservasjonsnøkkelOppgaveOppslag.hentÅpneOppgaverForReservasjonsnøkkel(område, reservasjonsnøkkel, tx)
        if (!sjekkTilganger(område, oppgaverForReservasjonsnøkkel, reserverForId, utføresAvId)) {
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
        val reservasjon = reservasjonV3Repository.lagreReservasjon(område, reservasjonTilLagring, tx)
        log.info("taReservasjon: Ny reservasjon $reservasjon, utført av $utføresAvId, for saksbehandler $reserverForId")
        køpåvirkendeHendelseChannel.send(ReservasjonTatt(område = område, reservasjonsnøkkel = reservasjonsnøkkel))
        return reservasjon
    }

    fun tellReservasjonerForSaksbehandlere(område: Områder, saksbehandlerIder: Set<Long>, tx: TransactionalSession): Map<Long, Int> {
        return reservasjonV3Repository.tellAktiveReservasjonerForSaksbehandlere(område, saksbehandlerIder, tx)
    }

    fun hentReservasjonerForSaksbehandler(område: Områder, saksbehandlerId: Long): List<ReservasjonV3MedOppgaver> {
        return transactionalManager.transaction { tx ->
            val reservasjoner =
                reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(område, saksbehandlerId, tx)

            reservasjoner.map { reservasjon ->
                finnOppgaverFor(område, reservasjon, tx)
            }

        }
    }

    fun finnOppgaverFor(
        område: Områder,
        reservasjon: ReservasjonV3,
        tx: TransactionalSession
    ): ReservasjonV3MedOppgaver {
        return ReservasjonV3MedOppgaver(
            reservasjon,
            reservasjonsnøkkelOppgaveOppslag.hentÅpneOppgaverForReservasjonsnøkkel(område, reservasjon.reservasjonsnøkkel, tx)
        )
    }

    fun annullerReservasjonHvisFinnes(
        område: Områder,
        reservasjonsnøkkel: String,
        kommentar: String?,
        annullertAvBrukerId: Long?,
        tx: TransactionalSession
    ): Boolean {
        val aktivReservasjon = reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(område, reservasjonsnøkkel, tx)
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
                køpåvirkendeHendelseChannel.send(ReservasjonAnnullert(område = område, reservasjonsnøkkel = reservasjonsnøkkel))
            }
            return true
        }
        return false
    }


    fun annullerReservasjonHvisFinnes(
        område: Områder,
        reservasjonsnøkkel: String,
        kommentar: String?,
        annullertAvBrukerId: Long?
    ): Boolean {
        return transactionalManager.transaction { tx ->
            annullerReservasjonHvisFinnes(område, reservasjonsnøkkel, kommentar, annullertAvBrukerId, tx)
        }
    }

    fun forlengReservasjon(
        område: Områder,
        reservasjonsnøkkel: String,
        nyTildato: LocalDateTime?,
        utførtAvBrukerId: Long,
        kommentar: String?,
    ): ReservasjonV3MedOppgaver {
        return transactionalManager.transaction { tx ->
            val aktivReservasjon = finnAktivReservasjon(område, reservasjonsnøkkel, tx)
            val nyReservasjon = reservasjonV3Repository.forlengReservasjon(
                område = område,
                aktivReservasjon = aktivReservasjon,
                endretAvBrukerId = utførtAvBrukerId,
                nyTildato = nyTildato ?: aktivReservasjon.gyldigTil.leggTilDagerHoppOverHelg(1),
                kommentar = kommentar ?: aktivReservasjon.kommentar,
                tx = tx
            )

            finnOppgaverFor(område, nyReservasjon, tx)
        }
    }

    fun overførReservasjon(
        område: Områder,
        reservasjonsnøkkel: String,
        reserverTil: LocalDateTime,
        tilSaksbehandlerId: Long,
        utførtAvBrukerId: Long,
        kommentar: String,
    ): ReservasjonV3MedOppgaver {
        return transactionalManager.transaction { tx ->
            sjekkSaksbehandlerErIOmråde(område, tilSaksbehandlerId)
            val aktivReservasjon = finnAktivReservasjon(område, reservasjonsnøkkel, tx)
            val nyReservasjon = reservasjonV3Repository.overførReservasjon(
                område = område,
                aktivReservasjon = aktivReservasjon,
                saksbehandlerSomSkalHaReservasjonId = tilSaksbehandlerId,
                endretAvBrukerId = utførtAvBrukerId,
                reserverTil = reserverTil,
                kommentar = kommentar,
                tx = tx
            )
            finnOppgaverFor(område, nyReservasjon, tx)
        }
    }

    fun endreReservasjon(
        område: Områder,
        reservasjonsnøkkel: String,
        endretAvBrukerId: Long,
        nyTildato: LocalDateTime?,
        nySaksbehandlerId: Long?,
        kommentar: String?
    ): ReservasjonV3MedOppgaver {
        return transactionalManager.transaction { tx ->
            nySaksbehandlerId?.let { sjekkSaksbehandlerErIOmråde(område, it) }
            val aktivReservasjon = finnAktivReservasjon(område, reservasjonsnøkkel, tx)

            val nyReservasjon = reservasjonV3Repository.endreReservasjon(
                område = område,
                reservasjonSomSkalEndres = aktivReservasjon,
                endretAvBrukerId = endretAvBrukerId,
                nyTildato = nyTildato,
                nySaksbehandlerId = nySaksbehandlerId,
                kommentar = kommentar,
                tx = tx
            )
            runBlocking {
                køpåvirkendeHendelseChannel.send(ReservasjonEndret(område = område, reservasjonsnøkkel = reservasjonsnøkkel))
            }
            finnOppgaverFor(område, nyReservasjon, tx)
        }
    }

    fun hentAlleAktiveReservasjoner(område: Områder): List<ReservasjonV3MedOppgaver> {
        return transactionalManager.transaction { tx ->
            val aktiveReservasjoner = reservasjonV3Repository.hentAlleAktiveReservasjoner(område, tx)
            aktiveReservasjoner.map { reservasjon ->
                finnOppgaverFor(område, reservasjon, tx)
            }
        }
    }

    private suspend fun sjekkTilganger(
        område: Områder,
        oppgaver: List<Oppgave>,
        brukerIdSomSkalHaReservasjon: Long,
        utføresAvId: Long,
    ): Boolean {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(brukerIdSomSkalHaReservasjon)!!
        // Reserverer innlogget bruker for seg selv, brukes innlogget brukers token.
        // Ellers må tilgang sjekkes for den andre saksbehandleren.
        val idTokenInnloggetBruker = if (brukerIdSomSkalHaReservasjon == utføresAvId) {
            coroutineContext.idToken()
        } else null

        return oppgaver.all { oppgave ->
            if (beslutterErSaksbehandler(
                    oppgave,
                    saksbehandler
                )
            ) throw ManglerTilgangException("Saksbehandler kan ikke være beslutter på egen behandling")

            if (idTokenInnloggetBruker != null) {
                pepClient.harTilgangTilOppgaveV3(område, idTokenInnloggetBruker, oppgave, Action.reserver)
            } else {
                pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver)
            }
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
        val saksbehandlerIdentSomSkalHaReservasjon = saksbehandler.navident

        return ansvarligSaksbehandlerIdent == saksbehandlerIdentSomSkalHaReservasjon
    }

    private fun sjekkSaksbehandlerErIOmråde(område: Områder, saksbehandlerId: Long) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(saksbehandlerId)
        if (saksbehandler == null || område !in saksbehandler.områder) {
            throw FinnerIkkeDataException("Fant ikke saksbehandler med id $saksbehandlerId i område ${område.eksternId}")
        }
    }

    fun finnAktivReservasjon(
        område: Områder,
        reservasjonsnøkkel: String,
    ): ReservasjonV3? {
        return transactionalManager.transaction { tx ->
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(område, reservasjonsnøkkel, tx)
        }
    }

    private fun finnAktivReservasjon(
        område: Områder,
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val aktivReservasjon =
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(område, reservasjonsnøkkel, tx)
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
