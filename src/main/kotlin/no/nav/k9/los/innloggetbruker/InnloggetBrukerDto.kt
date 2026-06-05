package no.nav.k9.los.innloggetbruker

data class InnloggetBrukerDto(
    val brukernavn: String,
    val navn: String,
    val brukerIdent: String?,
    val id: Long?,
    val kanSaksbehandle: Boolean,
    val kanOppgavestyre: Boolean,
    val kanReservere: Boolean,
    val kanDrifte: Boolean,
    val finnesISaksbehandlerTabell: Boolean
)
