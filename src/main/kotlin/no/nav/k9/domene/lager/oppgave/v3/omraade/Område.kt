package no.nav.k9.domene.lager.oppgave.v3.omraade

import no.nav.k9.domene.lager.oppgave.v3.datatype.Feltdefinisjon
import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavetype

class Område(
    // TODO kanskje kalle dette for navn?
    val område: String,
    val datatyper: Set<Feltdefinisjon>,
    val definisjonskilde: String,
    val oppgavetyper: Set<Oppgavetype>
) {
    fun sjekkEndringer(område: Område) {
        TODO("Not yet implemented")
    }
}