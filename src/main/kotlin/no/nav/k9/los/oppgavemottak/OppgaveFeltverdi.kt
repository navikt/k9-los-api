package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavefelt

data class OppgaveFeltverdi(
    val id: Long? = null,
    val oppgavefelt: Oppgavefelt,
    val verdi: String,
    val verdiBigInt: Long?
) {
    override fun toString(): String {
        return "OppgaveFeltverdi(id=$id, oppgavefeltnavn=${oppgavefelt.feltDefinisjon.eksternId}, verdi='$verdi'${verdiBigInt?.let { ", verdiBigInt=$it" } ?: ""})"
    }
}