package no.nav.k9.los.domeneadaptere.k9.eventmottak.kafka

data class Metadata(
    val version: Int,
    val correlationId: String,
    val requestId: String
)