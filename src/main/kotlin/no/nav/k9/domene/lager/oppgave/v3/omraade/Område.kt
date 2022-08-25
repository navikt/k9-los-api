package no.nav.k9.domene.lager.oppgave.v3.omraade

import no.nav.k9.domene.lager.oppgave.v3.datatype.Datatype
import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavetype

class Område(
    // TODO kanskje kalle dette for navn?
    val område: String,
    val datatyper: Set<Datatype>,
    val definisjonskilde: String,
    val oppgavetyper: Set<Oppgavetype>
) {
    fun sjekkEndringer(område: Område) {
        TODO("Not yet implemented")
    }
}