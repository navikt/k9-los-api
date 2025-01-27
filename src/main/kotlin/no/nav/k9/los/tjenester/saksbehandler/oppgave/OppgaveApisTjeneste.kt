package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.utils.leggTilDagerHoppOverHelg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class OppgaveApisTjeneste(
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val oppgaveV1Repository: no.nav.k9.los.domene.repository.OppgaveRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val oppgaveV3Repository: OppgaveRepository,
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3DtoBuilder: ReservasjonV3DtoBuilder,
    private val reservasjonOversetter: ReservasjonOversetter,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger("OppgaveApisTjeneste")
    }

    suspend fun reserverOppgave(
        innloggetBruker: Saksbehandler,
        oppgaveIdMedOverstyringDto: OppgaveIdMedOverstyringDto
    ): OppgaveStatusDto {
        val reserverFra = LocalDateTime.now()
        val oppgaveNøkkel = oppgaveIdMedOverstyringDto.oppgaveNøkkel
        /*
         1. Reserver i V1-modellen
         2. Reserver i V3-modellen
         3. Returner status fra V3.
         -- V1 er i praksis en skyggekopi for sikring av evt rollback
         */

        // Fjernes når V1 skal vekk
        oppgaveTjeneste.reserverOppgave(
            innloggetBruker.brukerIdent!!,
            oppgaveIdMedOverstyringDto.overstyrIdent,
            UUID.fromString(oppgaveNøkkel.oppgaveEksternId),
            oppgaveIdMedOverstyringDto.overstyrSjekk,
            oppgaveIdMedOverstyringDto.overstyrBegrunnelse
        )

        val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            oppgaveIdMedOverstyringDto.overstyrIdent ?: innloggetBruker.brukerIdent!!
        )!!

        if (oppgaveNøkkel.erV1Oppgave()) {
            val reservasjonV3 = reservasjonOversetter.taNyReservasjonFraGammelKontekst(
                oppgaveV1 = oppgaveV1Repository.hent(UUID.fromString(oppgaveNøkkel.oppgaveEksternId)),
                reserverForSaksbehandlerId = reserverForSaksbehandler.id!!,
                reservertTil = reserverFra.leggTilDagerHoppOverHelg(2),
                utførtAvSaksbehandlerId = innloggetBruker.id!!,
                kommentar = oppgaveIdMedOverstyringDto.overstyrBegrunnelse ?: "",
            )!!
            val saksbehandlerSomHarReservasjon =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)!!
            return OppgaveStatusDto(reservasjonV3, innloggetBruker, saksbehandlerSomHarReservasjon)
        } else {
            val reservasjonV3 = transactionalManager.transaction { tx ->
                val oppgaveV3 = oppgaveV3Repository.hentNyesteOppgaveForEksternId(
                    tx,
                    oppgaveNøkkel.områdeEksternId,
                    oppgaveNøkkel.oppgaveEksternId
                )

                reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktivMenSjekkLegacyFørst(
                    reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
                    reserverForId = reserverForSaksbehandler.id!!,
                    gyldigFra = reserverFra,
                    utføresAvId = innloggetBruker.id!!,
                    kommentar = oppgaveIdMedOverstyringDto.overstyrBegrunnelse,
                    gyldigTil = reserverFra.leggTilDagerHoppOverHelg(2),
                    tx = tx
                )
            }

            val saksbehandlerSomHarReservasjon =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)!!
            return OppgaveStatusDto(reservasjonV3, innloggetBruker, saksbehandlerSomHarReservasjon)
        }
    }

    suspend fun endreReservasjoner(
        reservasjonEndringDto: List<ReservasjonEndringDto>,
        innloggetBruker: Saksbehandler
    ) {
        reservasjonEndringDto.forEach {
            endreReservasjon(
                innloggetBruker,
                it.oppgaveNøkkel,
                it.brukerIdent,
                it.reserverTil,
                it.begrunnelse
            )
        }
    }

    private suspend fun endreReservasjon(
        innloggetBruker: Saksbehandler,
        oppgaveNøkkel: OppgaveNøkkelDto,
        tilBrukerIdent: String? = null,
        reserverTil: LocalDate? = null,
        begrunnelse: String? = null
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        try {
            oppgaveTjeneste.endreReservasjonPåOppgave(oppgaveNøkkel, tilBrukerIdent, reserverTil, begrunnelse)
        } catch (_: NullPointerException) {
        } catch (_: IllegalArgumentException) {
            //ReservasjonV1 annullerer noen reservasjoner som V3 ikke annullerer, og da kan det hende at det ikke finnes
            //noen V1-reservasjon å endre på
        }

        val tilSaksbehandler =
            tilBrukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it) }

        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(oppgaveNøkkel)
        val nyReservasjon = reservasjonV3Tjeneste.endreReservasjon(
            reservasjonsnøkkel = reservasjonsnøkkel,
            endretAvBrukerId = innloggetBruker.id!!,
            nyTildato = reserverTil?.let {
                LocalDateTime.of(
                    reserverTil,
                    LocalTime.MAX
                )
            },
            nySaksbehandlerId = tilSaksbehandler?.id,
            kommentar = begrunnelse
        )

        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(nyReservasjon.reservasjonV3.reservertAv)!!
        log.info("endreReservasjon: ${oppgaveNøkkel.oppgaveEksternId}, ${nyReservasjon.reservasjonV3}, reservertAv: $reservertAv")

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(nyReservasjon, reservertAv)
    }

    suspend fun forlengReservasjon(
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        try {
            oppgaveTjeneste.forlengReservasjonPåOppgave(UUID.fromString(forlengReservasjonDto.oppgaveNøkkel.oppgaveEksternId))
        } catch (e: NullPointerException) {
            //ReservasjonV1 annullerer noen reservasjoner som V3 ikke annullerer, og da kan det hende at det ikke finnes
            //noen V1-reservasjon å endre på
        }

        val reservasjonsnøkkel =
            reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(forlengReservasjonDto.oppgaveNøkkel)

        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                reservasjonsnøkkel = reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id!!,
                kommentar = forlengReservasjonDto.kommentar
            )

        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon.reservasjonV3.reservertAv)!!
        log.info("forlengReservasjon: ${forlengReservasjonDto.oppgaveNøkkel.oppgaveEksternId}, ${forlengetReservasjon.reservasjonV3}, reservertAv: $reservertAv")

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(forlengetReservasjon, reservertAv)
    }

    suspend fun overførReservasjon(
        params: FlyttReservasjonId,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        try {
            oppgaveTjeneste.flyttReservasjon(
                UUID.fromString(params.oppgaveNøkkel.oppgaveEksternId),
                params.brukerIdent,
                params.begrunnelse
            )
        } catch (e: NullPointerException) {
            //ReservasjonV1 annullerer noen reservasjoner som V3 ikke annullerer, og da kan det hende at det ikke finnes
            //noen V1-reservasjon å endre på
        }

        val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            params.brukerIdent
        )!!

        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(params.oppgaveNøkkel)

        val nyReservasjon = reservasjonV3Tjeneste.overførReservasjon(
            reservasjonsnøkkel = reservasjonsnøkkel,
            reserverTil = LocalDateTime.now().leggTilDagerHoppOverHelg(1),
            tilSaksbehandlerId = tilSaksbehandler.id!!,
            utførtAvBrukerId = innloggetBruker.id!!,
            kommentar = params.begrunnelse,
        )
        log.info("overførReservasjon: ${params.oppgaveNøkkel.oppgaveEksternId}, ${nyReservasjon.reservasjonV3}, utførtAv: $innloggetBruker., tilSaksbehandler: $tilSaksbehandler")

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(nyReservasjon, tilSaksbehandler)
    }

    private suspend fun annullerReservasjon(
        innloggetBruker: Saksbehandler,
        oppgaveNøkkelDto: OppgaveNøkkelDto,
    ) {
        // Fjernes når V1 skal vekk
        try {
            oppgaveTjeneste.frigiReservasjon(
                uuid = UUID.fromString(oppgaveNøkkelDto.oppgaveEksternId),
                begrunnelse = ""
            )
        } catch (e: NullPointerException) {
            //ReservasjonV1 annullerer noen reservasjoner som V3 ikke annullerer, og da kan det hende at det ikke finnes
            //noen V1-reservasjon å endre på
        }

        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(oppgaveNøkkelDto)

        val annulleringUtført = reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
            reservasjonsnøkkel = reservasjonsnøkkel,
            null,
            annullertAvBrukerId = innloggetBruker.id!!
        )
        log.info("annullerReservasjon: ${oppgaveNøkkelDto.oppgaveEksternId}, utførtAv: $innloggetBruker, $annulleringUtført")
    }

    suspend fun annullerReservasjoner(
        params: List<AnnullerReservasjon>,
        innloggetBruker: Saksbehandler
    ) {
        params.forEach {
            annullerReservasjon(
                innloggetBruker,
                it.oppgaveNøkkel,
            )
        }
    }

    suspend fun hentReserverteOppgaverForSaksbehandler(saksbehandler: Saksbehandler): List<ReservasjonV3Dto> {
        val reservasjonerMedOppgaver =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler.id!!)

        return reservasjonerMedOppgaver.map { reservasjonMedOppgaver ->
            try {
                reservasjonV3DtoBuilder.byggReservasjonV3Dto(reservasjonMedOppgaver, saksbehandler)
            } catch (e : Exception){
                log.warn("Klarte ikke tolke reservasjon med id ${reservasjonMedOppgaver.reservasjonV3.id}, v1-oppgave: ${reservasjonMedOppgaver.oppgaveV1?.eksternId} v3-oppgaver: ${reservasjonMedOppgaver.oppgaverV3.map { it.eksternId } }")
                throw e;
            }
        }
    }
}