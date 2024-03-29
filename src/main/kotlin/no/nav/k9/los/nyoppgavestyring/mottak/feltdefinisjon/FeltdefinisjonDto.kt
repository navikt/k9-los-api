package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.los.spi.felter.TransientFeltutleder

data class FeltdefinisjonDto(
    val id: String,
    val visningsnavn: String,
    val listetype: Boolean,
    val tolkesSom: String,
    val visTilBruker: Boolean,
    val kokriterie: Boolean,
    val kodeverkreferanse: KodeverkReferanseDto?,
    val transientFeltutleder: String?
)
