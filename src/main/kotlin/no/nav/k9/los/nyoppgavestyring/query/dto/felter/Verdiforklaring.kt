package no.nav.k9.los.nyoppgavestyring.query.dto.felter

import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Synlighet

class Verdiforklaring(
    val verdi: String,
    val visningsnavn: String,
    val synlighet: Synlighet,
    val gruppering: String?,
    val rekkefølge: Int? = null
)