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
            val listeMedAlle = punsj()
            listeMedAlle.addAll(fraK9Sak)
            return listeMedAlle
        }

        private fun punsj(): MutableList<Aksjonspunkt>{
            return mutableListOf(Aksjonspunkt("PUNSJ", "Punsj oppgave", "MANU", "", "", null, false),
            Aksjonspunkt("UTLØPT", "Utløpt oppgave", "MANU", "", "", null, false),
            Aksjonspunkt("MER_INFORMASJON", "Venter på informasjon", "MANU", "", "", null, false))
        }
    }
}
