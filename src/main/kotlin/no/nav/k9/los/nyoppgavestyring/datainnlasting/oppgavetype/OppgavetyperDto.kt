package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype

data class OppgavetyperDto (
    val område: String,
    val definisjonskilde: String,
    val oppgavetyper: Set<OppgavetypeDto>
)