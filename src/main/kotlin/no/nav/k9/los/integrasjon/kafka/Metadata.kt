package no.nav.k9.los.integrasjon.kafka

data class Metadata(
    val version: Int,
    val correlationId: String,
    val requestId: String
)