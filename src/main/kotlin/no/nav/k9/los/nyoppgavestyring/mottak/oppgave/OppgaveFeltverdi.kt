package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt

data class OppgaveFeltverdi(
    val id: Long? = null,
    val oppgavefelt: Oppgavefelt,
    val verdi: String,
    val oppgavestatus: Oppgavestatus?,
) {
    override fun toString(): String {
        return "OppgaveFeltverdi(id=$id, oppgavefeltnavn=${oppgavefelt.feltDefinisjon.eksternId}, verdi='$verdi')"
    }
}