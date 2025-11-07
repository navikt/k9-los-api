package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Avstemmingsrapport
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Behandlingstilstand
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Oppgavetilstand
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave

object SakAvstemmer {
    fun regnUtDiff(k9SakRapport: List<Behandlingstilstand>, åpneLosOppgaver: List<Oppgave>): Avstemmingsrapport {
        val forekomsterMedUliktInnhold = mutableSetOf<Pair<Behandlingstilstand, Oppgavetilstand>>()
        val forekomsterSomGranskesManuelt = mutableSetOf<Pair<Behandlingstilstand, Oppgavetilstand>>()
        val åpneForekomsterIFagsystemSomManglerILos = mutableSetOf<Behandlingstilstand>()
        k9SakRapport.forEach { behandling ->
            val oppgave = åpneLosOppgaver.find { it.eksternId == behandling.eksternId }
            if (oppgave == null) {
                åpneForekomsterIFagsystemSomManglerILos.add(behandling)
            } else {
                val sammenligning =
                    FuzzySammenligningsresultat.sammenlign(behandling.behandlingStatus, Oppgavestatus.fraKode(oppgave.status))
                if (sammenligning == FuzzySammenligningsresultat.ULIK) {
                    forekomsterMedUliktInnhold.add(Pair(behandling, Oppgavetilstand(oppgave)))
                } else if (sammenligning == FuzzySammenligningsresultat.VETIKKE) {
                    forekomsterSomGranskesManuelt.add(Pair(behandling, Oppgavetilstand(oppgave)))
                }
            }
        }

        val åpneForekomsterILosSomManglerIFagsystem = mutableSetOf<Oppgavetilstand>()
        åpneLosOppgaver.forEach { oppgave ->
            val overlapp = k9SakRapport.find { it.eksternId == oppgave.eksternId }
            if (overlapp == null) {
                åpneForekomsterILosSomManglerIFagsystem.add(Oppgavetilstand(oppgave))
            }
        }

        return Avstemmingsrapport(
            "K9Sak",
            åpneForekomsterIFagsystemSomManglerILos.toList(),
            åpneForekomsterILosSomManglerIFagsystem.toList(),
            forekomsterMedUliktInnhold.toList(),
            forekomsterSomGranskesManuelt.toList()
            )
    }
}

enum class FuzzySammenligningsresultat {
    LIK,
    ULIK,
    VETIKKE;

    companion object {
        fun sammenlign(behandlingStatus: BehandlingStatus, oppgavestatus: Oppgavestatus): FuzzySammenligningsresultat {
            return when(behandlingStatus) {
                BehandlingStatus.AVSLUTTET -> if (oppgavestatus == Oppgavestatus.LUKKET) { LIK } else { ULIK }
                BehandlingStatus.OPPRETTET ->
                    if (oppgavestatus == Oppgavestatus.LUKKET ) { //UAVKLART etter merge
                        LIK
                    } else if (oppgavestatus == Oppgavestatus.LUKKET) {
                        ULIK
                    } else {
                        VETIKKE
                    }

                BehandlingStatus.FATTER_VEDTAK,
                BehandlingStatus.IVERKSETTER_VEDTAK,
                BehandlingStatus.UTREDES -> if (oppgavestatus == Oppgavestatus.LUKKET) { ULIK } else { VETIKKE }

                BehandlingStatus.SATT_PÅ_VENT,
                BehandlingStatus.LUKKET,
                BehandlingStatus.SENDT_INN -> throw IllegalStateException("Bare aktuell for punsjoppgaver")
            }
        }
    }
}