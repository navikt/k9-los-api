package no.nav.k9.nyoppgavestyring.feltdefinisjon

data class FeltdefinisjonDto(
    val id: String,
    val listetype: Boolean,
    val tolkesSom: String,
    val visTilBruker: Boolean
)
