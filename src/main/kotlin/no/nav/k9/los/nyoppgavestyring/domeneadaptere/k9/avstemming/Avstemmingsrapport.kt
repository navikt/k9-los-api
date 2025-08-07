package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave

data class Avstemmingsrapport(
    val fagsystem: String,
    val forekomsterIFagsystemSomManglerILos: List<Behandlingstilstand>,
    val forekomsterILosSomManglerIFagsystem: List<Oppgave>,
    val forekomsterMedUliktInnhold: List<Pair<Behandlingstilstand, Oppgave>>,
    val forekomsterSomGranskesManuelt: List<Pair<Behandlingstilstand, Oppgave>>,
)