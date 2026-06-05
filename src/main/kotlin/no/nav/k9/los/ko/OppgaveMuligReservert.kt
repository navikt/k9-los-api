package no.nav.k9.los.ko

import no.nav.k9.los.reservasjon.ReservasjonV3
import no.nav.k9.los.oppgaveuthenting.Oppgave

sealed class OppgaveMuligReservert {
    data class Reservert(val oppgave: Oppgave, val reservasjon: ReservasjonV3) : OppgaveMuligReservert()
    data object IkkeReservert : OppgaveMuligReservert()
}
