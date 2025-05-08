package no.nav.k9.los.tjenester.saksbehandler

data class InnloggetNavAnsattDto(
    val brukernavn: String,
    val navn: String,
    val brukerIdent: String?,
    val kanSaksbehandle: Boolean,
    val kanOppgavestyre: Boolean,
    val kanReservere: Boolean,
    val kanDrifte: Boolean,
    val finnesISaksbehandlerTabell: Boolean
)
