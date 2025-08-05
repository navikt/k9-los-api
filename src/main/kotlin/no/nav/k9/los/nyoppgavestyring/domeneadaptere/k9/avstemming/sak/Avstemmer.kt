package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.sak

import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Avstemmingsrapport
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.sak.kontrakt.avstemming.produksjonsstyring.Behandlingstilstand

object Avstemmer {
    fun regnUtDiff(k9SakRapport: List<Behandlingstilstand>, åpneLosOppgaver: List<Oppgave>): Avstemmingsrapport {
        val forekomsterMedUliktInnhold = mutableSetOf<Pair<Behandlingstilstand, Oppgave>>()
        val forekomsterSomGranskesManuelt = mutableSetOf<Pair<Behandlingstilstand, Oppgave>>()
        val åpneForekomsterIFagsystemSomManglerILos = mutableSetOf<Behandlingstilstand>()
        k9SakRapport.forEach { behandling ->
            val oppgave = åpneLosOppgaver.find { it.eksternId == behandling.behandlingUuid.behandlingUuid.toString() }
            if (oppgave == null) {
                åpneForekomsterIFagsystemSomManglerILos.add(behandling)
            } else {
                val sammenligning =
                    FuzzyOppgavestatus.sammenlign(behandling.behandlingStatus, Oppgavestatus.fraKode(oppgave.status))
                if (sammenligning == FuzzyOppgavestatus.ULIK) {
                    forekomsterMedUliktInnhold.add(Pair(behandling, oppgave))
                } else if (sammenligning == FuzzyOppgavestatus.VETIKKE) {
                    forekomsterSomGranskesManuelt.add(Pair(behandling, oppgave))
                }
            }
        }

        val åpneForekomsterILosSomManglerIFagsystem = mutableSetOf<Oppgave>()
        åpneLosOppgaver.forEach { oppgave ->
            val overlapp = k9SakRapport.find { it.behandlingUuid.behandlingUuid.toString() == oppgave.eksternId }
            if (overlapp == null) {
                åpneForekomsterILosSomManglerIFagsystem.add(oppgave)
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

enum class FuzzyOppgavestatus {
    LIK,
    ULIK,
    VETIKKE;

    companion object {
        fun sammenlign(behandlingStatus: BehandlingStatus, oppgavestatus: Oppgavestatus): FuzzyOppgavestatus {
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
            }
        }
    }
}