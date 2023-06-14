package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

data class AnnullerReservasjonDto(
    val saksbehandlerEpost: String,
    val reservasjonsnøkkel: String,
)
