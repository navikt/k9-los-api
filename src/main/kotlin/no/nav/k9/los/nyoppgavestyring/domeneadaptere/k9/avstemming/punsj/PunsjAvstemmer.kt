package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Avstemmingsrapport
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Oppgavetilstand
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave


object PunsjAvstemmer {
    fun regnUtDiff(
        k9PunsjRapport: List<Journalposttilstand>,
        åpneLosOppgaver: List<Oppgave>
    ): Avstemmingsrapport.JournalpostRapport {
        val forekomsterMedUliktInnhold = mutableListOf<Pair<Journalposttilstand, Oppgavetilstand>>()
        val forekomsterSomGranskesManuelt = mutableListOf<Pair<Journalposttilstand, Oppgavetilstand>>()
        val åpneForekomsterIFagsystemSomManglerILos = mutableListOf<Journalposttilstand>()
        k9PunsjRapport.forEach { journalpost ->
            val oppgave = åpneLosOppgaver.find { it.hentVerdi("journalpostId") == journalpost.journalpostId }
            if (oppgave == null) {
                åpneForekomsterIFagsystemSomManglerILos.add(journalpost)
            } else {
                val sammenligning =
                    FuzzySammenligningsresultat.sammenlign(journalpost, oppgave)
                if (sammenligning == FuzzySammenligningsresultat.ULIK) {
                    forekomsterMedUliktInnhold.add(Pair(journalpost, Oppgavetilstand(oppgave)))
                } else if (sammenligning == FuzzySammenligningsresultat.VETIKKE) {
                    forekomsterSomGranskesManuelt.add(Pair(journalpost, Oppgavetilstand(oppgave)))
                }
            }
        }

        val åpneForekomsterILosSomManglerIFagsystem = mutableListOf<Oppgavetilstand>()
        åpneLosOppgaver.forEach { oppgave ->
            val overlapp = k9PunsjRapport.find { it.journalpostId == oppgave.hentVerdi("journalpostId") }
            if (overlapp == null) {
                åpneForekomsterILosSomManglerIFagsystem.add(Oppgavetilstand(oppgave))
            }
        }

        return Avstemmingsrapport.JournalpostRapport(
            Fagsystem.PUNSJ.kode,
            åpneForekomsterIFagsystemSomManglerILos.size,
            åpneForekomsterILosSomManglerIFagsystem.size,
            forekomsterMedUliktInnhold.size,
            forekomsterSomGranskesManuelt.size,
            åpneForekomsterIFagsystemSomManglerILos,
            åpneForekomsterILosSomManglerIFagsystem,
            forekomsterMedUliktInnhold,
            forekomsterSomGranskesManuelt
        )
    }
}


enum class FuzzySammenligningsresultat {
    LIK,
    ULIK,
    VETIKKE;

    companion object {
        fun sammenlign(
            journalposttilstand: Journalposttilstand,
            oppgave: Oppgave
        ): FuzzySammenligningsresultat {
            val oppgavestatus = Oppgavestatus.fraKode(oppgave.status)
            if (oppgavestatus != Oppgavestatus.AAPEN) {
                return ULIK
            }
            journalposttilstand.ytelseType?.let {
                val ytelseType = FagsakYtelseType.fraKode(oppgave.hentVerdi("ytelsestype")!!)
                if (FagsakYtelseType.fraKode(journalposttilstand.ytelseType) != ytelseType) {
                    return ULIK
                }
            }

            return LIK
        }
    }
}