package no.nav.k9.los.oppgaveuthenting.query.dto.felter

import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet

class Verdiforklaring(
    val verdi: String,
    val visningsnavn: String,
    val synlighet: Synlighet,
    val gruppering: String?,
    val rekkefølge: Int? = null
)