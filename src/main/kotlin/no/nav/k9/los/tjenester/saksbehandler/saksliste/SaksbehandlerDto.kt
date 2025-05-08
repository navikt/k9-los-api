package no.nav.k9.los.tjenester.saksbehandler.saksliste

data class SaksbehandlerDto(
    val id: Long?,
    val brukerIdent: String?,
    val navn: String?,
    val epost: String,
    val enhet: String?,
    val oppgavekoer: List<String>
)
