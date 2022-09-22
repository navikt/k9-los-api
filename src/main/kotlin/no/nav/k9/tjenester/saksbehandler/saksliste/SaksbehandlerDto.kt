package no.nav.k9.tjenester.saksbehandler.saksliste

class SaksbehandlerDto(
    val brukerIdent: String?,
    val navn: String?,
    var epost: String,
    var enhet: String?,
    val oppgavekoer: List<String>
)
