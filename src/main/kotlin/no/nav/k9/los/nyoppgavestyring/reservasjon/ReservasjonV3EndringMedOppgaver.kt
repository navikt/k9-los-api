package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave


data class ReservasjonV3EndringMedOppgaver(
    val reservasjonV3: ReservasjonV3MedEndring,
    val oppgaverV3: List<Oppgave>,
    val oppgaveV1: no.nav.k9.los.domene.lager.oppgave.Oppgave?
)