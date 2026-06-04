package no.nav.k9.los.nyoppgavestyring.ko

import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.uthenting.Oppgave

sealed class OppgaveMuligReservert {
    data class Reservert(val oppgave: Oppgave, val reservasjon: ReservasjonV3) : OppgaveMuligReservert()
    data object IkkeReservert : OppgaveMuligReservert()
}
