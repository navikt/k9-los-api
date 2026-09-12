package no.nav.k9.los.innloggetbruker

import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger

data class InnloggetBrukerDtoNy(
    val epost: String,
    val navn: String,
    val brukerIdent: String,
    val tilganger: Tilganger,
    val id: Long?,
    val finnesISaksbehandlerTabell: Boolean
)
