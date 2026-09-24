package no.nav.k9.los.reservasjon

import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9FagsakYtelseType
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelUtenOmrådeDto
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.AktivOppgaveOppslag
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReservasjonApisTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3DtoBuilder: ReservasjonV3DtoBuilder,
    private val aktivOppgaveOppslag: AktivOppgaveOppslag,
    private val pepClient: IPepClient,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger("OppgaveApisTjeneste")
    }

    suspend fun reserverOppgave(
        område: Områder,
        kode6: Boolean,
        navIdent: String,
        oppgaveNøkkel: OppgaveNøkkelUtenOmrådeDto
    ): OppgaveStatusDto {
        val reserverFra = LocalDateTime.now()

        val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            navIdent,
            kode6
        )!!

        val reservasjonV3 = transactionalManager.transactionSuspend { tx ->
            val oppgave = aktivOppgaveOppslag.hentAktivOppgave(
                område = område,
                oppgaveNøkkel.oppgaveEksternId,
                oppgaveNøkkel.oppgaveTypeEksternId,
                tx
            )

            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                område = område,
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                reserverForId = reserverForSaksbehandler.id,
                gyldigFra = reserverFra,
                utføresAvId = reserverForSaksbehandler.id,
                kommentar = null,
                gyldigTil = reserverFra.leggTilDagerHoppOverHelg(2),
                tx = tx
            )
        }

        return OppgaveStatusDto(reservasjonV3, reserverForSaksbehandler)
    }

    fun endreReservasjoner(
        område: Områder,
        reservasjonEndringDto: List<ReservasjonEndringDto>,
        innloggetBruker: Saksbehandler
    ) {
        reservasjonEndringDto.forEach {
            endreReservasjon(
                område,
                innloggetBruker,
                it,
                it.brukerIdent,
                it.reserverTil,
                it.begrunnelse
            )
        }
    }

    private fun endreReservasjon(
        område: Områder,
        innloggetBruker: Saksbehandler,
        endringDto: ReservasjonEndringDto,
        tilBrukerIdent: String? = null,
        reserverTil: LocalDate? = null,
        begrunnelse: String? = null
    ) {
        val tilSaksbehandler =
            tilBrukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it, innloggetBruker.skjermet) }

        val nyReservasjon = reservasjonV3Tjeneste.endreReservasjon(
            område = område,
            reservasjonsnøkkel = endringDto.reservasjonsnøkkel,
            endretAvBrukerId = innloggetBruker.id,
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
        log.info("endreReservasjon: ${nyReservasjon.reservasjonV3}, reservertAv: $reservertAv")
    }

    suspend fun forlengReservasjon(
        område: Områder,
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        val (reservasjon, reservertAv) = forleng(område, forlengReservasjonDto, innloggetBruker)
        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(reservasjon, reservertAv)
    }

    suspend fun forlengReservasjonNy(
        område: Områder,
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonMedOppgaverDto {
        val (reservasjon, reservertAv) = forleng(område, forlengReservasjonDto, innloggetBruker)
        return tilReservasjonMedOppgaverDto(reservasjon, reservertAv)
    }

    private fun forleng(
        område: Områder,
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler
    ): Pair<ReservasjonV3MedOppgaver, Saksbehandler> {
        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                område = område,
                reservasjonsnøkkel = forlengReservasjonDto.reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id,
                kommentar = forlengReservasjonDto.kommentar
            )

        val reservertAv =
            saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon.reservasjonV3.reservertAv)!!
        log.info("forlengReservasjon: ${forlengetReservasjon.reservasjonV3}, reservertAv: $reservertAv")

        return forlengetReservasjon to reservertAv
    }

    suspend fun overførReservasjon(
        område: Områder,
        params: FlyttReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        val (reservasjon, tilSaksbehandler) = overfør(område, params, innloggetBruker)
        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(reservasjon, tilSaksbehandler)
    }

    suspend fun overførReservasjonNy(
        område: Områder,
        params: FlyttReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonMedOppgaverDto {
        val (reservasjon, tilSaksbehandler) = overfør(område, params, innloggetBruker)
        return tilReservasjonMedOppgaverDto(reservasjon, tilSaksbehandler)
    }

    private fun overfør(
        område: Områder,
        params: FlyttReservasjonDto,
        innloggetBruker: Saksbehandler
    ): Pair<ReservasjonV3MedOppgaver, Saksbehandler> {
        val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            params.brukerIdent,
            innloggetBruker.skjermet
        )!!

        val nyReservasjon = reservasjonV3Tjeneste.overførReservasjon(
            område = område,
            reservasjonsnøkkel = params.reservasjonsnøkkel,
            reserverTil = LocalDateTime.now().leggTilDagerHoppOverHelg(1),
            tilSaksbehandlerId = tilSaksbehandler.id,
            utførtAvBrukerId = innloggetBruker.id,
            kommentar = params.begrunnelse,
        )
        log.info("overførReservasjon: ${nyReservasjon.reservasjonV3}, utførtAv: $innloggetBruker., tilSaksbehandler: $tilSaksbehandler")

        return nyReservasjon to tilSaksbehandler
    }

    private fun annullerReservasjon(
        område: Områder,
        innloggetBruker: Saksbehandler,
        annullerReservasjon: AnnullerReservasjonDto,
    ) {
        val annulleringUtført = reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
            område = område,
            reservasjonsnøkkel = annullerReservasjon.reservasjonsnøkkel,
            null,
            annullertAvBrukerId = innloggetBruker.id
        )
        log.info("annullerReservasjon, utførtAv: $innloggetBruker, $annulleringUtført")
    }

    fun annullerReservasjoner(
        område: Områder,
        params: List<AnnullerReservasjonDto>,
        innloggetBruker: Saksbehandler
    ) {
        params.forEach {
            annullerReservasjon(
                område,
                innloggetBruker,
                it,
            )
        }
    }

    suspend fun hentReserverteOppgaverForSaksbehandler(område: Områder, saksbehandler: Saksbehandler): List<ReservasjonV3Dto> {
        val reservasjonerMedOppgaver =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(område, saksbehandler.id)

        return reservasjonerMedOppgaver.map { reservasjonMedOppgaver ->
            try {
                reservasjonV3DtoBuilder.byggReservasjonV3Dto(reservasjonMedOppgaver, saksbehandler)
            } catch (e: Exception) {
                log.warn("Klarte ikke tolke reservasjon med id ${reservasjonMedOppgaver.reservasjonV3.id}, v3-oppgaver: ${reservasjonMedOppgaver.oppgaverV3.map { it.eksternId }}")
                throw e
            }
        }
    }

    suspend fun hentReserverteOppgaverForSaksbehandlerNy(
        område: Områder,
        saksbehandler: Saksbehandler
    ): List<ReservasjonMedOppgaverDto> {
        return reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(område, saksbehandler.id)
            .map { tilReservasjonMedOppgaverDto(it, saksbehandler) }
    }

    suspend fun hentAktivReservasjon(område: Områder, idToken: IIdToken, oppgaveNøkkel: OppgaveNøkkelDto): ReservasjonV3Dto? {
        val (reservasjon, reservertAv) = finnAktivReservasjon(område, idToken, oppgaveNøkkel) ?: return null
        return ReservasjonV3Dto(
            reservasjonV3 = reservasjon,
            oppgaver = emptyList(),
            reservertAv = reservertAv,
            endretAvNavn = null
        )
    }

    suspend fun hentAktivReservasjonNy(
        område: Områder,
        idToken: IIdToken,
        oppgaveNøkkel: OppgaveNøkkelDto
    ): ReservasjonsinfoDto? {
        val (reservasjon, reservertAv) = finnAktivReservasjon(område, idToken, oppgaveNøkkel) ?: return null
        return ReservasjonsinfoDto(reservasjon, reservertAv, endretAvNavn(reservasjon))
    }

    private suspend fun finnAktivReservasjon(
        område: Områder,
        idToken: IIdToken,
        oppgaveNøkkel: OppgaveNøkkelDto
    ): Pair<ReservasjonV3, Saksbehandler>? {
        val oppgave = aktivOppgaveOppslag.hentAktivOppgave(
                område,
                oppgaveNøkkel.oppgaveEksternId,
                oppgaveNøkkel.oppgaveTypeEksternId,
            )
        if (!pepClient.harTilgangTilOppgaveV3(område, idToken, oppgave, Action.read)) {
            throw ManglerTilgangException("Mangler tilgang til oppgave ${oppgave.eksternId}")
        }
        val reservasjon = reservasjonV3Tjeneste.finnAktivReservasjon(område, oppgave.reservasjonsnøkkel)
            ?: return null
        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(reservasjon.reservertAv)
            ?: throw IllegalStateException("Fant ikke saksbehandler med id ${reservasjon.reservertAv} som har reservert oppgave ${oppgave.eksternId}")
        return reservasjon to reservertAv
    }

    private suspend fun tilReservasjonMedOppgaverDto(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        reservertAv: Saksbehandler,
    ): ReservasjonMedOppgaverDto {
        val reservasjon = reservasjonMedOppgaver.reservasjonV3
        return ReservasjonMedOppgaverDto(
            reservasjon = ReservasjonsinfoDto(reservasjon, reservertAv, endretAvNavn(reservasjon)),
            oppgaver = oppgaveSammendragDtoBuilder.bygg(reservasjonMedOppgaver.oppgaverV3),
        )
    }

    private fun endretAvNavn(reservasjon: ReservasjonV3): String? =
        reservasjon.endretAv?.let { saksbehandlerRepository.finnSaksbehandlerMedId(it)?.navn }

    suspend fun hentAlleAktiveReservasjonerNy(område: Områder, kode6: Boolean): List<ReservasjonMedOppgaverDto> {
        return reservasjonV3Tjeneste.hentAlleAktiveReservasjoner(område).mapNotNull { reservasjonMedOppgaver ->
            val reservertAv =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.reservertAv)!!
            if (kode6 != reservertAv.skjermet) {
                null
            } else {
                tilReservasjonMedOppgaverDto(reservasjonMedOppgaver, reservertAv)
            }
        }
    }

    fun hentAlleAktiveReservasjoner(område: Områder, kode6: Boolean): List<ReservasjonDto> {
        return reservasjonV3Tjeneste.hentAlleAktiveReservasjoner(område).flatMap { reservasjonMedOppgaver ->
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.reservertAv)!!
            val saksbehandlerHarKode6Tilgang = saksbehandler.skjermet

            if (kode6 != saksbehandlerHarKode6Tilgang) {
                emptyList()
            } else {
                reservasjonMedOppgaver.oppgaverV3.map { oppgave ->
                    ReservasjonDto(
                        reservertAvEpost = saksbehandler.epost,
                        reservertAvIdent = saksbehandler.navident!!,
                        reservertAvId = saksbehandler.id,
                        reservertAvNavn = saksbehandler.navn,
                        saksnummer = oppgave.hentVerdi("saksnummer"), //TODO: Oppgaveagnostisk logikk. Løses antagelig ved å skrive om frontend i dette tilfellet
                        journalpostId = oppgave.hentVerdi("journalpostId"),
                        ytelse = oppgave.hentVerdi("ytelsestype")?.let { K9FagsakYtelseType.fraKode(it).navn }
                            ?: K9FagsakYtelseType.UKJENT.navn,
                        behandlingType = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!),
                        reservertTilTidspunkt = reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                        kommentar = reservasjonMedOppgaver.reservasjonV3.kommentar ?: "",
                        tilBeslutter = oppgave.hentVerdi("liggerHosBeslutter").toBoolean(),
                        oppgavenøkkel = OppgaveNøkkelDto(oppgave),
                        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                    )
                }.toList()
            }
        }
    }
}
