package no.nav.k9.los.nyoppgavestyring.spi.felter

data class SqlMedParams (
    val query: String = "",
    val queryParams: Map<String, Any?> = mapOf()
)