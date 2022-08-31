package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

data class FeltdefinisjonDto(
    val id: String,
    val listetype: Boolean,
    val parsesSom: String,
    val visTilBruker: Boolean
)
