package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class OppgaveApisTjeneste(
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val oppgaveV1Repository: no.nav.k9.los.domene.repository.OppgaveRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val oppgaveV3Repository: OppgaveRepository,
    private val oppgaveV3RepositoryMedTxWrapper: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper,
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3DtoBuilder: ReservasjonV3DtoBuilder,
    private val reservasjonOversetter: ReservasjonOversetter,
) {

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
        val oppgaveStatusDto = oppgaveTjeneste.reserverOppgave(
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
                reservertTil = reserverFra.plusHours(48).forskyvReservasjonsDato(),
                utførtAvSaksbehandlerId = innloggetBruker.id!!,
                kommentar = oppgaveIdMedOverstyringDto.overstyrBegrunnelse ?: "",
            )!!
            val saksbehandlerSomHarReservasjon =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)
            return OppgaveStatusDto(reservasjonV3, innloggetBruker, saksbehandlerSomHarReservasjon)
        } else {
            val reservasjonV3 = transactionalManager.transaction { tx ->
                val oppgaveV3 = oppgaveV3Repository.hentNyesteOppgaveForEksternId(
                    tx,
                    oppgaveNøkkel.områdeEksternId,
                    oppgaveNøkkel.oppgaveEksternId
                )

                reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                    reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
                    reserverForId = reserverForSaksbehandler.id!!,
                    gyldigFra = reserverFra,
                    utføresAvId = innloggetBruker.id!!,
                    kommentar = oppgaveIdMedOverstyringDto.overstyrBegrunnelse ?: "",
                    gyldigTil = reserverFra.plusHours(48).forskyvReservasjonsDato(),
                    tx = tx
                )
            }

            val saksbehandlerSomHarReservasjon =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)
            return OppgaveStatusDto(reservasjonV3, innloggetBruker, saksbehandlerSomHarReservasjon)
        }
    }

    suspend fun endreReservasjon(
        reservasjonEndringDto: ReservasjonEndringDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        try {
            oppgaveTjeneste.endreReservasjonPåOppgave(reservasjonEndringDto)
        } catch (e: NullPointerException) {
            //ReservasjonV1 annullerer noen reservasjoner som V3 ikke annullerer, og da kan det hende at det ikke finnes
            //noen V1-reservasjon å endre på
        }

        val tilSaksbehandler =
            reservasjonEndringDto.brukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it) }

        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(reservasjonEndringDto.oppgaveNøkkel)
        val nyReservasjon =
            reservasjonV3Tjeneste.endreReservasjon(
                reservasjonsnøkkel = reservasjonsnøkkel,
                endretAvBrukerId = innloggetBruker.id!!,
                nyTildato = reservasjonEndringDto.reserverTil?.let {
                    LocalDateTime.of(
                        reservasjonEndringDto.reserverTil,
                        LocalTime.MAX
                    )
                },
                nySaksbehandlerId = tilSaksbehandler?.id,
                kommentar = reservasjonEndringDto.begrunnelse
            )

        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(nyReservasjon!!.reservertAv)

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

        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(forlengReservasjonDto.oppgaveNøkkel)

        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                reservasjonsnøkkel = reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id!!,
                kommentar = forlengReservasjonDto.kommentar ?: ""
            )

        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon!!.reservertAv)!!

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
            reserverTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato(),
            tilSaksbehandlerId = tilSaksbehandler.id!!,
            utførtAvBrukerId = innloggetBruker.id!!,
            kommentar = params.begrunnelse,
        )

        return reservasjonV3DtoBuilder.byggReservasjonV3Dto(nyReservasjon, tilSaksbehandler)
    }

    suspend fun annullerReservasjon(
        params: OpphevReservasjonId,
        innloggetBruker: Saksbehandler
    ) {
        // Fjernes når V1 skal vekk
        try {
            oppgaveTjeneste.frigiReservasjon(UUID.fromString(params.oppgaveNøkkel.oppgaveEksternId), params.begrunnelse)
        } catch (e: NullPointerException) {
            //ReservasjonV1 annullerer noen reservasjoner som V3 ikke annullerer, og da kan det hende at det ikke finnes
            //noen V1-reservasjon å endre på
        }

        val reservasjonsnøkkel = reservasjonOversetter.hentReservasjonsnøkkelForOppgavenøkkel(params.oppgaveNøkkel)

        reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
            reservasjonsnøkkel,
            params.begrunnelse,
            innloggetBruker.id!!
        )
    }

    suspend fun hentReserverteOppgaverForSaksbehandler(saksbehandler: Saksbehandler): List<ReservasjonV3Dto> {
        val reservasjoner =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler.id!!)

        return reservasjoner.map { reservasjon ->
            reservasjonV3DtoBuilder.byggReservasjonV3Dto(reservasjon, saksbehandler)
        }
    }
}