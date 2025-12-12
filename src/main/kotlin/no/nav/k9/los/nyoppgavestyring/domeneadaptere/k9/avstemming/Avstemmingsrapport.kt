package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

data class Avstemmingsrapport(
    val fagsystem: String,
    val antallForekomsterIFagsystemSomManglerILos: Int,
    val antallforekomsterILosSomManglerIFagsystem: Int,
    val antallforekomsterMedUliktInnhold: Int,
    val antallforekomsterSomGranskesManuelt: Int,
    val forekomsterIFagsystemSomManglerILos: List<Behandlingstilstand>,
    val forekomsterILosSomManglerIFagsystem: List<Oppgavetilstand>,
    val forekomsterMedUliktInnhold: List<Pair<Behandlingstilstand, Oppgavetilstand>>,
    val forekomsterSomGranskesManuelt: List<Pair<Behandlingstilstand, Oppgavetilstand>>,
)