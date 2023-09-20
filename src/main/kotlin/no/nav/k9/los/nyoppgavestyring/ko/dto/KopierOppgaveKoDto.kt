package no.nav.k9.los.nyoppgavestyring.ko.dto

data class KopierOppgaveKoDto(
    val kopierFraOppgaveId: Long,
    val tittel: String,
    val taMedQuery: Boolean,
    val taMedSaksbehandlere: Boolean
)