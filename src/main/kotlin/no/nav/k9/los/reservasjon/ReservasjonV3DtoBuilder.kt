package no.nav.k9.los.reservasjon

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository

class ReservasjonV3DtoBuilder(
    private val pdlService: IPdlService,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pepClient: IPepClient,
) {
    suspend fun byggReservasjonV3Dto(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        saksbehandler: Saksbehandler,
        brukerkontekst: BrukerkontekstMedOmråde
    ): ReservasjonV3Dto {
        return byggForV3(reservasjonMedOppgaver, saksbehandler, brukerkontekst)
    }


    suspend fun byggForV3(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        saksbehandler: Saksbehandler,
        brukerkontekst: BrukerkontekstMedOmråde
    ): ReservasjonV3Dto {
        brukerkontekst.krevOmråde(reservasjonMedOppgaver.reservasjonV3.område)
        if (!brukerkontekst.harBasisTilgang) throw ManglerTilgangException("Mangler basistilgang")
        if (saksbehandler.skjermet != brukerkontekst.harTilgangTilKode6) throw ManglerTilgangException("Reservasjonen tilhører en annen skjerming")
        var endretAvNavn: String? = null
        if (reservasjonMedOppgaver.reservasjonV3.endretAv != null) {
            endretAvNavn =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.endretAv)?.navn
        }

        val oppgaveV3Dtos = reservasjonMedOppgaver.oppgaverV3.filter {
            it.oppgavetype.område.eksternId == brukerkontekst.område.eksternId &&
                pepClient.harTilgangTilOppgaveV3(it, brukerkontekst)
        }.map {
            val person = it.hentVerdi("aktorId")?.let {
                aktørId -> pdlService.person(aktørId, brukerkontekst).person
            }
            GenerellOppgaveV3Dto(it, person)
        }
        return ReservasjonV3Dto(reservasjonMedOppgaver.reservasjonV3, oppgaveV3Dtos, saksbehandler, endretAvNavn)
    }
}
