package no.nav.k9.domene.modell


import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.tjenester.mock.Aksjonspunkt

class AksjonspunktDefWrapper {

    companion object {
        fun fraKode(kode: String): AksjonspunktDefinisjon? {
           return AksjonspunktDefinisjon.fraKode(kode)
        }

        fun påVent(liste: Map<String, String>): Boolean {
            return liste.map { entry -> AksjonspunktDefinisjon.fraKode(entry.key) }.any { it.erAutopunkt() }
        }

        fun tilBeslutter(liste: Map<String, String>): Boolean {
            return liste.map { entry -> AksjonspunktDefinisjon.fraKode(entry.key) }
                .all { it == AksjonspunktDefinisjon.FATTER_VEDTAK }
        }

        fun inneholderFatterVedtak(liste: Map<String, String>): Boolean {
            return liste.map { entry -> AksjonspunktDefinisjon.fraKode(entry.key) }.contains(AksjonspunktDefinisjon.FATTER_VEDTAK)
        }

        fun inneholderEtAktivtAksjonspunktMedKoden(liste: Map<String, String>, def: AksjonspunktDefinisjon): Boolean {
            val definisjon = liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> entry.key }
                .find { kode -> kode == def.kode }
            return definisjon != null
        }

        fun inneholderEtInaktivtAksjonspunktMedKoden(liste: Map<String, String>, def: AksjonspunktDefinisjon): Boolean {
            val definisjon = liste.filter { entry -> entry.value != "OPPR"}
                .map { entry -> entry.key }
                .find { kode -> kode == def.kode }
            return definisjon != null
        }

        fun finnAlleAksjonspunkter(): List<Aksjonspunkt> {
            val fraK9Sak = AksjonspunktDefinisjon.values().filter { it.kode != null }.map {
                Aksjonspunkt(
                    kode = it.kode,
                    navn = it.navn,
                    aksjonspunktype = it.aksjonspunktType.name,
                    behandlingsstegtype = it.behandlingSteg.name,
                    plassering = "",
                    totrinn = it.defaultTotrinnBehandling,
                    vilkårtype = it.vilkårType?.name
                )
            }
            val listeMedAlle = aksjonspunkterFraPunsj()
            listeMedAlle.addAll(fraK9Sak)
            return listeMedAlle
        }

        fun aksjonspunkterFraPunsj(): MutableList<Aksjonspunkt>{
            return mutableListOf(Aksjonspunkt("PUNSJ", "Punsj oppgave", "MANU", "", "", null, false),
            Aksjonspunkt("UTLØPT", "Utløpt oppgave", "MANU", "", "", null, false),
            Aksjonspunkt("MER_INFORMASJON", "Venter på informasjon", "MANU", "", "", null, false))
        }
    }
}
