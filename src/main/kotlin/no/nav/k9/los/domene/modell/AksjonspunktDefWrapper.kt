package no.nav.k9.los.domene.modell


import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.tjenester.mock.AksjonspunktMock

class AksjonspunktDefWrapper {
    companion object {
        fun påVent(fagsystem: Fagsystem, liste: Map<String, String>): Boolean {
            return liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> FagsystemAksjonspunktDefinisjoner.fraKode(fagsystem, entry.key) }
                .any { it.erAutopunkt }
        }

        fun tilBeslutter(liste: Map<String, String>): Boolean {
            return liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> AksjonspunktDefinisjon.fraKode(entry.key) }
                .all { it == AksjonspunktDefinisjon.FATTER_VEDTAK }
        }
    }
}
