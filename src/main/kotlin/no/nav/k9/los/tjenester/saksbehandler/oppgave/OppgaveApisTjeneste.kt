package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
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

class OppgaveApisTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val oppgaveV3Repository: OppgaveRepository,
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3DtoBuilder: ReservasjonV3DtoBuilder,
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

        val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            oppgaveIdMedOverstyringDto.overstyrIdent ?: innloggetBruker.brukerIdent!!
        )!!

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
        val tilSaksbehandler =
            tilBrukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it) }

        val reservasjonsnøkkel = hentReservasjonsnøkkelForOppgavenøkkel(oppgaveNøkkel)
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
        val reservasjonsnøkkel =
            hentReservasjonsnøkkelForOppgavenøkkel(forlengReservasjonDto.oppgaveNøkkel)

        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                reservasjonsnøkkel = reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id!!,
                kommentar = forlengReservasjonDto.kommentar
            )

        val reservertAv =
            saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon.reservasjonV3.reservertAv)!!
        log.info("forlengReservasjon: ${forlengReservasjonDto.oppgaveNøkkel.oppgaveEksternId}, ${forlengetReservasjon.reservasjonV3}, reservertAv: $reservertAv")

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(forlengetReservasjon, reservertAv)
    }

    suspend fun overførReservasjon(
        params: FlyttReservasjonId,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            params.brukerIdent
        )!!

        val reservasjonsnøkkel = hentReservasjonsnøkkelForOppgavenøkkel(params.oppgaveNøkkel)

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
        val reservasjonsnøkkel = hentReservasjonsnøkkelForOppgavenøkkel(oppgaveNøkkelDto)

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
            } catch (e: Exception) {
                log.warn("Klarte ikke tolke reservasjon med id ${reservasjonMedOppgaver.reservasjonV3.id}, v1-oppgave: ${reservasjonMedOppgaver.oppgaveV1?.eksternId} v3-oppgaver: ${reservasjonMedOppgaver.oppgaverV3.map { it.eksternId }}")
                throw e;
            }
        }
    }

    fun hentReservasjonsnøkkelForOppgavenøkkel(
        oppgaveNøkkel: OppgaveNøkkelDto
    ): String {
        return transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentNyesteOppgaveForEksternId(
                tx,
                oppgaveNøkkel.områdeEksternId,
                oppgaveNøkkel.oppgaveEksternId
            ).reservasjonsnøkkel
        }
    }
}