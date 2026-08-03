package no.nav.k9.los.ko.dto

data class KopierOppgaveKoDto(
    val kopierFraOppgaveId: Long,
    val tittel: String,
    val taMedQuery: Boolean,
    val taMedSaksbehandlere: Boolean
)