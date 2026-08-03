package no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder

data class SqlMedParams (
    val query: String = "",
    val queryParams: Map<String, Any?> = mapOf()
)