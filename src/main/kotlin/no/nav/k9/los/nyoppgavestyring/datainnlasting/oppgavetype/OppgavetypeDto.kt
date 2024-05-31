package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype

data class OppgavetypeDto (
    val id: String,
    var oppgavebehandlingsUrlTemplate: String,
    val oppgavefelter: Set<OppgavefeltDto>
)