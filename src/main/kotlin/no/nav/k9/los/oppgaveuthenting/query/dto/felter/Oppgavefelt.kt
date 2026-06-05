package no.nav.k9.los.oppgaveuthenting.query.dto.felter

import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet

class Oppgavefelt(
    val område: String?,
    val kode: String,
    val visningsnavn: String,
    val tolkes_som: String,
    val synlighet: Synlighet,
    val listetype: Boolean = false,
    val verdiforklaringerErUttømmende: Boolean = false,
    val verdiforklaringer: List<Verdiforklaring>?
)
