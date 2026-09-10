package no.nav.k9.los.oppgaveuthenting.query.dto.felter

import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class Oppgavefelt(
    val område: Områder?,
    val kode: String,
    val visningsnavn: String,
    val tolkes_som: String,
    val synlighet: Synlighet,
    val listetype: Boolean = false,
    val verdiforklaringerErUttømmende: Boolean = false,
    val verdiforklaringer: List<Verdiforklaring>?
)
