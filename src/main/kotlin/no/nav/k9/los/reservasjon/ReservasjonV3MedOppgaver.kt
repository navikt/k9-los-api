package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.Oppgave


data class ReservasjonV3MedOppgaver(
    val reservasjonV3: ReservasjonV3,
    val oppgaverV3: List<Oppgave>,
)