package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.felter

import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.feltdefinisjon.Synlighet

class Verdiforklaring(
    val verdi: String,
    val visningsnavn: String,
    val synlighet: Synlighet,
    val gruppering: String?,
    val rekkefølge: Int? = null
)