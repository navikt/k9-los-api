package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.KodeverkSynlighet

data class KodeverkVerdiDto(
    val verdi: String,
    val visningsnavn: String,
    val synlighet: KodeverkSynlighet,
    val beskrivelse: String? = null,
    val gruppering: String? = null,
    val rekkefølge: Int? = null,
    @Deprecated("Bruk synlighet-feltet i stedet")
    val favoritt: Boolean = false
)