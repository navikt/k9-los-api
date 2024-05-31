package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype

data class OppgavetypeEndring(
    val oppgavetype: Oppgavetype,
    val felterSomSkalLeggesTil: List<Oppgavefelt>,
    val felterSomSkalFjernes: List<Oppgavefelt>,
    val felterSomSkalEndresMedNyeVerdier: List<OppgavefeltDelta>
)

data class OppgavefeltDelta(
    val eksisterendeFelt: Oppgavefelt,
    val innkommendeFelt: Oppgavefelt
)