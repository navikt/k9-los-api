package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveDto
import java.time.LocalDateTime

data class ReservasjonV3Dto (
    val reserverteV3Oppgaver: List<GenerellOppgaveV3Dto>,

    // Fjernes når V1 skal vekk
    val reservertOppgaveV1Dto: OppgaveDto? = null,

    val reservasjonsnøkkel: String,
    val reservertAvIdent: String,
    val reservertAvEpost: String,
    val kommentar: String,
    val reservertFra: LocalDateTime,
    val reservertTil: LocalDateTime?,
) {
    constructor(reservasjonV3: ReservasjonV3, oppgaver: List<GenerellOppgaveV3Dto>, reservertAv: Saksbehandler) : this (
        reserverteV3Oppgaver = oppgaver,
        reservasjonsnøkkel = reservasjonV3.reservasjonsnøkkel,
        reservertAvIdent = reservertAv.brukerIdent!!,
        reservertAvEpost = reservertAv.epost,
        kommentar = reservasjonV3.kommentar,
        reservertFra = reservasjonV3.gyldigFra,
        reservertTil = reservasjonV3.gyldigTil
    )

    // Fjernes når V1 skal vekk
    constructor(reservasjonV3: ReservasjonV3, oppgave: OppgaveDto?, reservertAv: Saksbehandler) : this (
        reserverteV3Oppgaver = emptyList(),
        reservertOppgaveV1Dto = oppgave,
        reservasjonsnøkkel = reservasjonV3.reservasjonsnøkkel,
        reservertAvIdent = reservertAv.brukerIdent!!,
        reservertAvEpost = reservertAv.epost,
        kommentar = reservasjonV3.kommentar,
        reservertFra = reservasjonV3.gyldigFra,
        reservertTil = reservasjonV3.gyldigTil
    )
}