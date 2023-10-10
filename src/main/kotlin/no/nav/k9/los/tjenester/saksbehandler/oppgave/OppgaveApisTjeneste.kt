package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class OppgaveApisTjeneste(
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val oppgaveV3Repository: OppgaveRepository,
    private val oppgaveV3Tjeneste: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveTjeneste,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveKoTjeneste: OppgaveKoTjeneste,
    private val transactionalManager: TransactionalManager,
) {

    suspend fun reserverOppgave(
        innloggetBruker: Saksbehandler,
        oppgaveIdMedOverstyring: OppgaveIdMedOverstyring
    ): OppgaveStatusDto {
        val reserverFra = LocalDateTime.now()
        /*
         1. Reserver i V1-modellen
         2. Reserver i V3-modellen
         3. Returner status fra V3.
         -- V1 er i praksis en skyggekopi for sikring av evt rollback
         */

        // Fjernes når V1 skal vekk
        oppgaveTjeneste.reserverOppgave(
            innloggetBruker.brukerIdent!!,
            oppgaveIdMedOverstyring.overstyrIdent,
            UUID.fromString(oppgaveIdMedOverstyring.oppgaveId),
            oppgaveIdMedOverstyring.overstyrSjekk,
            oppgaveIdMedOverstyring.overstyrBegrunnelse
        )

        val oppgaveV3 = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, oppgaveIdMedOverstyring.oppgaveId)
        }

        val reserverForIdent = oppgaveIdMedOverstyring.overstyrIdent ?: innloggetBruker.brukerIdent
        val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(reserverForIdent!!)!!
        val reservasjonV3 = reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
            reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
            reserverForId = reserverForSaksbehandler.id!!,
            gyldigFra = reserverFra,
            utføresAvId = innloggetBruker.id!!,
            kommentar = oppgaveIdMedOverstyring.overstyrBegrunnelse ?: "",
            gyldigTil = reserverFra.plusHours(24).forskyvReservasjonsDato()
        )
        //TODO: sjekke statusobjekt, saksbehandler som holder reservasjon -- feks conflict hvis noen andre hadde reservasjon fra før
        val saksbehandlerSomHarReservasjon =
            saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)
        val oppgaveStatusDto = OppgaveStatusDto(
            erReservert = true,
            reservertTilTidspunkt = reservasjonV3.gyldigTil,
            erReservertAvInnloggetBruker = reservasjonV3.reservertAv == innloggetBruker.id!!,
            reservertAv = saksbehandlerSomHarReservasjon.brukerIdent,
            reservertAvNavn = saksbehandlerSomHarReservasjon.navn,
            flyttetReservasjon = null,
            kanOverstyres = reservasjonV3.reservertAv != innloggetBruker.id!!
        )
        return oppgaveStatusDto
    }

    suspend fun endreReservasjon(
        reservasjonEndringDto: ReservasjonEndringDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.endreReservasjonPåOppgave(reservasjonEndringDto)

        val tilSaksbehandler =
            reservasjonEndringDto.brukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it) }

        val oppgave =
            oppgaveV3Tjeneste.hentOppgave(reservasjonEndringDto.oppgaveId) //TODO oppgaveId er behandlingsUUID?
        val nyReservasjon =
            reservasjonV3Tjeneste.endreReservasjon(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
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

        val åpneOppgaverForReservasjonsnøkkel =
            oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)
        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(nyReservasjon!!.reservertAv)

        val reservasjonV3Dto = ReservasjonV3Dto(nyReservasjon, åpneOppgaverForReservasjonsnøkkel, reservertAv)
        return reservasjonV3Dto
    }

    fun forlengReservasjon(
        forlengReservasjonDto: ForlengReservasjonDto,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.forlengReservasjonPåOppgave(UUID.fromString(forlengReservasjonDto.oppgaveId))

        //TODO oppgaveId er behandlingsUUID?
        val oppgave = oppgaveV3Tjeneste.hentOppgave(forlengReservasjonDto.oppgaveId)
        //TODO: Oppgavetype som ikke er støttet i V3 -- utlede reservasjonsnøkkel

        val forlengetReservasjon =
            reservasjonV3Tjeneste.forlengReservasjon(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                nyTildato = forlengReservasjonDto.nyTilDato,
                utførtAvBrukerId = innloggetBruker.id!!,
                kommentar = forlengReservasjonDto.kommentar ?: ""
            )

        val åpneOppgaverForReservasjonsnøkkel =
            oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)
        val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon!!.reservertAv)!!

        val reservasjonV3Dto = ReservasjonV3Dto(
            forlengetReservasjon!!,
            åpneOppgaverForReservasjonsnøkkel,
            reservertAv
        )
        return reservasjonV3Dto
    }

    suspend fun overførReservasjon(
        params: FlyttReservasjonId,
        innloggetBruker: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.flyttReservasjon(
            UUID.fromString(params.oppgaveId),
            params.brukerIdent,
            params.begrunnelse
        )

        val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
            params.brukerIdent
        )!!

        val oppgave = oppgaveV3Tjeneste.hentOppgave(params.oppgaveId)
        //TODO: Oppgavetype som ikke er støttet i V3 -- utlede reservasjonsnøkkel

        val nyReservasjon = reservasjonV3Tjeneste.overførReservasjon(
            reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
            reserverTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato(),
            tilSaksbehandlerId = tilSaksbehandler.id!!,
            utførtAvBrukerId = innloggetBruker.id!!,
            kommentar = params.begrunnelse,
        )

        val åpneOppgaverForReservasjonsnøkkel =
            oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)

        return ReservasjonV3Dto(nyReservasjon, åpneOppgaverForReservasjonsnøkkel, tilSaksbehandler)
    }

    suspend fun annullerReservasjon(
        params: OpphevReservasjonId,
        innloggetBruker: Saksbehandler
    ) {
        // Fjernes når V1 skal vekk
        oppgaveTjeneste.frigiReservasjon(UUID.fromString(params.oppgaveId), params.begrunnelse)

        val oppgave = oppgaveV3Tjeneste.hentOppgave(params.oppgaveId)
        reservasjonV3Tjeneste.annullerReservasjon(
            oppgave.reservasjonsnøkkel,
            params.begrunnelse,
            innloggetBruker.id!!
        )
    }

    fun hentReserverteOppgaverForSaksbehandler(saksbehandler: Saksbehandler): List<ReservasjonV3Dto> {
        val reservasjoner =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler.id!!)

        return reservasjoner.map { reservasjon ->
            // Fjernes når V1 skal vekk
            val oppgaveV1 = reservasjonOversetter.hentV1OppgaveFraReservasjon(reservasjon)
            if (oppgaveV1 == null) {
                val oppgaverForReservasjonsnøkkel =
                    oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(reservasjon.reservasjonsnøkkel)
                ReservasjonV3Dto(
                    reservasjon,
                    oppgaverForReservasjonsnøkkel,
                    saksbehandler
                )
            } else {
                ReservasjonV3Dto(reservasjon, oppgaveV1.eksternId.toString(), saksbehandler)
            }
        }
    }

    fun hentAntallOppgaverIKø(oppgaveKoId: String) {
        oppgaveKoTjeneste.hentAntallOppgaveForKø(oppgaveKoId.toLong())
    }
}