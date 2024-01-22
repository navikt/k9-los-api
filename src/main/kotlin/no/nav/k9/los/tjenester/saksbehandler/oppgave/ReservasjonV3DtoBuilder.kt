package no.nav.k9.los.tjenester.saksbehandler.oppgave

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.fnr
import no.nav.k9.los.integrasjon.pdl.navn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper

class ReservasjonV3DtoBuilder(
    private val oppgaveRepositoryTxWrapper: OppgaveRepositoryTxWrapper,
    private val pdlService: IPdlService,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val oppgaveTjeneste: OppgaveTjeneste
) {
    suspend fun byggReservasjonV3Dto(
        reservasjon: ReservasjonV3,
        saksbehandler: Saksbehandler
    ): ReservasjonV3Dto {
        // Fjernes når V1 skal vekk
        val oppgaveV1 = reservasjonOversetter.hentV1OppgaveFraReservasjon(reservasjon)
        return if (oppgaveV1 == null) {
            val oppgaverForReservasjonsnøkkel =
                oppgaveRepositoryTxWrapper.hentÅpneOppgaverForReservasjonsnøkkel(reservasjon.reservasjonsnøkkel)

            val oppgaveV3Dtos = oppgaverForReservasjonsnøkkel.map { oppgave ->
                val person = pdlService.person(oppgave.hentVerdi("aktorId")!!).person!!
                GenerellOppgaveV3Dto(oppgave, person)
            }
            ReservasjonV3Dto(reservasjon, oppgaveV3Dtos, saksbehandler)
        } else {
            val person = pdlService.person(oppgaveV1.aktorId)
            val oppgaveV1Dto = OppgaveDto(
                status = OppgaveStatusDto(
                    true,
                    reservasjon.gyldigTil,
                    true,
                    saksbehandler.brukerIdent,
                    saksbehandler?.navn,
                    flyttetReservasjon = null
                ),
                behandlingId = oppgaveV1.behandlingId,
                saksnummer = oppgaveV1.fagsakSaksnummer,
                journalpostId = oppgaveV1.journalpostId,
                navn = person.person!!.navn(),
                system = oppgaveV1.system,
                personnummer = person.person!!.fnr(),
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
                avklarArbeidsforhold = oppgaveV1.avklarArbeidsforhold,
                merknad = oppgaveTjeneste.hentAktivMerknad(oppgaveV1.eksternId.toString())
            )
            ReservasjonV3Dto(reservasjon, oppgaveV1Dto, saksbehandler)
        }
    }
}