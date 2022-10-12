package no.nav.k9.los.integrasjon.pdl

data class PdlResponse(
        val ikkeTilgang: Boolean,
        val aktorId: AktøridPdl?
)
