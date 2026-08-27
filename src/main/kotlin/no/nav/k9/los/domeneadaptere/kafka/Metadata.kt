package no.nav.k9.los.domeneadaptere.kafka

data class Metadata(
    val version: Int,
    val correlationId: String,
    val requestId: String
)