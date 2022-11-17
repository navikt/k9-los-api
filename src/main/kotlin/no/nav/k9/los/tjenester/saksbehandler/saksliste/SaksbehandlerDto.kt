package no.nav.k9.los.tjenester.saksbehandler.saksliste

class SaksbehandlerDto(
    val brukerIdent: String?,
    val navn: String?,
    var epost: String,
    var enhet: String?,
    val oppgavekoer: List<String>
)
