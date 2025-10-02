package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReservasjonApisTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val oppgaveV3Repository: OppgaveRepository,
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3DtoBuilder: ReservasjonV3DtoBuilder,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val pepClient: IPepClient,
    private val azureGraphService: IAzureGraphService
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
        val reservasjonsnøkkel =
            reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(forlengReservasjonDto.oppgaveNøkkel)

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

    private fun annullerReservasjon(
        innloggetBruker: Saksbehandler,
        oppgaveNøkkelDto: OppgaveNøkkelDto,
    ) {
        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(oppgaveNøkkelDto)

        val annulleringUtført = reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
            reservasjonsnøkkel = reservasjonsnøkkel,
            null,
            annullertAvBrukerId = innloggetBruker.id!!
        )
        log.info("annullerReservasjon: ${oppgaveNøkkelDto.oppgaveEksternId}, utførtAv: $innloggetBruker, $annulleringUtført")
    }

    fun annullerReservasjoner(
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
                log.warn("Klarte ikke tolke reservasjon med id ${reservasjonMedOppgaver.reservasjonV3.id}, v3-oppgaver: ${reservasjonMedOppgaver.oppgaverV3.map { it.eksternId }}")
                throw e
            }
        }
    }

    suspend fun hentAktivReservasjon(oppgaveNøkkel: OppgaveNøkkelDto): ReservasjonV3Dto? {
        val oppgave = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentNyesteOppgaveForEksternId(
                tx,
                oppgaveNøkkel.områdeEksternId,
                oppgaveNøkkel.oppgaveEksternId
            )
        }
        if (!pepClient.harTilgangTilOppgaveV3(
                oppgave = oppgave,
                grupperForSaksbehandler = azureGraphService.hentGrupperForInnloggetSaksbehandler()
            )
        ) {
            throw ManglerTilgangException("Mangler tilgang til oppgave ${oppgave.eksternId}")
        }
        val reservasjon = reservasjonV3Tjeneste.finnAktivReservasjon(oppgave.reservasjonsnøkkel)
            ?: return null
        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv)
            ?: throw IllegalStateException("Fant ikke saksbehandler med id ${reservasjon.reservertAv} som har reservert oppgave ${oppgave.eksternId}")
        return ReservasjonV3Dto(
            reservasjonV3 = reservasjon,
            oppgaver = emptyList(),
            reservertAv = reservertAv,
            endretAvNavn = null
        )
    }

    suspend fun hentAlleAktiveReservasjoner(): List<ReservasjonDto> {
        val innloggetBrukerHarKode6Tilgang = pepClient.harTilgangTilKode6()

        return reservasjonV3Tjeneste.hentAlleAktiveReservasjoner().flatMap { reservasjonMedOppgaver ->
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.reservertAv)!!
            val saksbehandlerHarKode6Tilgang = pepClient.harTilgangTilKode6(saksbehandler.brukerIdent!!)

            if (innloggetBrukerHarKode6Tilgang != saksbehandlerHarKode6Tilgang) {
                emptyList()
            } else {
                reservasjonMedOppgaver.oppgaverV3.map { oppgave ->
                    ReservasjonDto(
                        reservertAvEpost = saksbehandler.epost,
                        reservertAvIdent = saksbehandler.brukerIdent!!,
                        reservertAvNavn = saksbehandler.navn,
                        saksnummer = oppgave.hentVerdi("saksnummer"), //TODO: Oppgaveagnostisk logikk. Løses antagelig ved å skrive om frontend i dette tilfellet
                        journalpostId = oppgave.hentVerdi("journalpostId"),
                        ytelse = oppgave.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it).navn }
                            ?: FagsakYtelseType.UKJENT.navn,
                        behandlingType = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!),
                        reservertTilTidspunkt = reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                        kommentar = reservasjonMedOppgaver.reservasjonV3.kommentar ?: "",
                        tilBeslutter = oppgave.hentVerdi("liggerHosBeslutter").toBoolean(),
                        oppgavenøkkel = OppgaveNøkkelDto(oppgave),
                    )
                }.toList()
            }
        }
    }
}