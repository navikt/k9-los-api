package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto

class ReservasjonV3DtoBuilder(
    private val pdlService: IPdlService,
    private val saksbehandlerRepository: SaksbehandlerRepository
) {
    suspend fun byggReservasjonV3Dto(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        saksbehandler: Saksbehandler
    ): ReservasjonV3Dto {
        return byggForV3(reservasjonMedOppgaver, saksbehandler)
    }


    suspend fun byggForV3(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        saksbehandler: Saksbehandler
    ): ReservasjonV3Dto {
        var endretAvNavn: String? = null
        if (reservasjonMedOppgaver.reservasjonV3.endretAv != null) {
            endretAvNavn =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.endretAv)?.navn
        }

        val oppgaveV3Dtos = reservasjonMedOppgaver.oppgaverV3.map {
            val person = it.hentVerdi("aktorId")?.let {
                aktørId -> pdlService.person(aktørId).person
            }
            GenerellOppgaveV3Dto(it, person)
        }
        return ReservasjonV3Dto(reservasjonMedOppgaver.reservasjonV3, oppgaveV3Dtos, saksbehandler, endretAvNavn)
    }
}