package no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl

data class PdlResponse(
        val ikkeTilgang: Boolean,
        val aktorId: AktøridPdl?
)
