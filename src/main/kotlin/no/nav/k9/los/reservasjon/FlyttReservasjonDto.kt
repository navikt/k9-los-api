package no.nav.k9.los.reservasjon

data class FlyttReservasjonDto(
    val reservasjonsnøkkel: String,
    val brukerIdent: String,
    val begrunnelse: String
)
