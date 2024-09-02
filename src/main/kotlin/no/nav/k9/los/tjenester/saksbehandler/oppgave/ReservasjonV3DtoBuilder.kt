package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.fnr
import no.nav.k9.los.integrasjon.pdl.navn
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3EndringMedOppgaver
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3MedOppgaver
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto

class ReservasjonV3DtoBuilder(
    private val pdlService: IPdlService,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository
) {
    suspend fun byggReservasjonV3Dto(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        saksbehandler: Saksbehandler
    ): ReservasjonV3Dto {
        if (reservasjonMedOppgaver.oppgaveV1 == null) {
            return byggForV3(reservasjonMedOppgaver, saksbehandler)
        } else {
            // Fjernes når V1 skal vekk
            return byggForV1(reservasjonMedOppgaver, saksbehandler)
        }
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

            val person = pdlService.person(it.hentVerdi("aktorId")!!).person
            GenerellOppgaveV3Dto(it, person)
        }
        return ReservasjonV3Dto(reservasjonMedOppgaver.reservasjonV3, oppgaveV3Dtos, saksbehandler, endretAvNavn)
    }


    suspend fun byggForV1(
        reservasjonMedOppgaver: ReservasjonV3MedOppgaver,
        saksbehandler: Saksbehandler
    ): ReservasjonV3Dto {
        val oppgaveV1 = reservasjonMedOppgaver.oppgaveV1!!
        var endretAvNavn: String? = null
        if (reservasjonMedOppgaver.reservasjonV3.endretAv != null) {
            endretAvNavn =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.endretAv)?.navn
        }

        val oppgaveV1Dto = if (oppgaveV1.aktiv) {
            val person = pdlService.person(oppgaveV1.aktorId)
            OppgaveDto(
                status = OppgaveStatusDto(
                    true,
                    reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                    true,
                    saksbehandler.brukerIdent,
                    saksbehandler?.navn,
                    flyttetReservasjon = null
                ),
                behandlingId = oppgaveV1.behandlingId,
                saksnummer = oppgaveV1.fagsakSaksnummer,
                journalpostId = oppgaveV1.journalpostId,
                navn = person.person?.navn() ?: "Ukjent navn",
                system = oppgaveV1.system,
                personnummer = person.person?.fnr() ?: "Ukjent fnummer",
                behandlingstype = oppgaveV1.behandlingType,
                fagsakYtelseType = oppgaveV1.fagsakYtelseType,
                behandlingStatus = oppgaveV1.behandlingStatus,
                erTilSaksbehandling = true,
                opprettetTidspunkt = oppgaveV1.behandlingOpprettet,
                behandlingsfrist = oppgaveV1.behandlingsfrist,
                eksternId = oppgaveV1.eksternId,
                tilBeslutter = oppgaveV1.tilBeslutter,
                utbetalingTilBruker = oppgaveV1.utbetalingTilBruker,
                selvstendigFrilans = oppgaveV1.selvstendigFrilans,
                søktGradering = oppgaveV1.søktGradering,
                avklarArbeidsforhold = oppgaveV1.avklarArbeidsforhold
            )
        } else {
            null
        }
        return ReservasjonV3Dto(reservasjonMedOppgaver.reservasjonV3, oppgaveV1Dto, saksbehandler, endretAvNavn)
    }
}