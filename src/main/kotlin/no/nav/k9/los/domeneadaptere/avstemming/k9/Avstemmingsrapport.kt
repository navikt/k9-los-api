package no.nav.k9.los.domeneadaptere.avstemming.k9

import no.nav.k9.los.domeneadaptere.avstemming.k9.punsj.Journalposttilstand
import no.nav.k9.los.domeneadaptere.avstemming.k9.saksbehandling.Behandlingstilstand

sealed class Avstemmingsrapport {
    data class BehandlingRapport(
        val fagsystem: String,
        val antallForekomsterIFagsystemSomManglerILos: Int,
        val antallforekomsterILosSomManglerIFagsystem: Int,
        val antallforekomsterMedUliktInnhold: Int,
        val antallforekomsterSomGranskesManuelt: Int,
        val forekomsterIFagsystemSomManglerILos: List<Behandlingstilstand>,
        val forekomsterILosSomManglerIFagsystem: List<Oppgavetilstand>,
        val forekomsterMedUliktInnhold: List<Pair<Behandlingstilstand, Oppgavetilstand>>,
        val forekomsterSomGranskesManuelt: List<Pair<Behandlingstilstand, Oppgavetilstand>>,
    ) : Avstemmingsrapport()

    data class JournalpostRapport(
        val fagsystem: String,
        val antallForekomsterIFagsystemSomManglerILos: Int,
        val antallforekomsterILosSomManglerIFagsystem: Int,
        val antallforekomsterMedUliktInnhold: Int,
        val antallforekomsterSomGranskesManuelt: Int,
        val forekomsterIFagsystemSomManglerILos: List<Journalposttilstand>,
        val forekomsterILosSomManglerIFagsystem: List<Oppgavetilstand>,
        val forekomsterMedUliktInnhold: List<Pair<Journalposttilstand, Oppgavetilstand>>,
        val forekomsterSomGranskesManuelt: List<Pair<Journalposttilstand, Oppgavetilstand>>,
    ) : Avstemmingsrapport()
}