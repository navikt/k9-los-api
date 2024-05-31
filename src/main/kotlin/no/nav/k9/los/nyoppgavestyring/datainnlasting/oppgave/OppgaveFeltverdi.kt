package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave

import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.Oppgavefelt

class OppgaveFeltverdi(
    val id: Long? = null,
    val oppgavefelt: Oppgavefelt,
    val verdi: String,
    val aktiv: Boolean,
    val oppgavestatus: Oppgavestatus?,
) {
    override fun toString(): String {
        return "OppgaveFeltverdi(id=$id, oppgavefeltnavn=${oppgavefelt.feltDefinisjon.eksternId}, verdi='$verdi')"
    }
}