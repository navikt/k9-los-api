package no.nav.k9.nyoppgavestyring.feltdefinisjon

data class FeltdefinisjonDto(
    val id: String,
    val listetype: Boolean,
    val parsesSom: String,
    val visTilBruker: Boolean
)
