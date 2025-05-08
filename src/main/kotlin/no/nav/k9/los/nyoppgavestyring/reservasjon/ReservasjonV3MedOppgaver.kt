package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave


data class ReservasjonV3MedOppgaver(
    val reservasjonV3: ReservasjonV3,
    val oppgaverV3: List<Oppgave>,
    val oppgaveV1: no.nav.k9.los.domene.lager.oppgave.Oppgave?
)