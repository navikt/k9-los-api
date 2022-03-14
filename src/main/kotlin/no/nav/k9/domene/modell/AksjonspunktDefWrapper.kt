package no.nav.k9.domene.modell


import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.tjenester.mock.AksjonspunktMock

class AksjonspunktDefWrapper {

    companion object {

        fun fraKode(kode: String): AksjonspunktDefinisjon? {
            return AksjonspunktDefinisjon.fraKode(kode)
        }

        fun påVent(liste: Map<String, String>): Boolean {
            return liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> AksjonspunktDefinisjon.fraKode(entry.key) }
                .any { it.erAutopunkt() }
        }

        fun tilBeslutter(liste: Map<String, String>): Boolean {
            return liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> AksjonspunktDefinisjon.fraKode(entry.key) }
                .all { it == AksjonspunktDefinisjon.FATTER_VEDTAK }
        }

        fun manuelleAksjonspunkter(liste: Map<String, String>): Map<AksjonspunktDefinisjon, String> {
            return liste.mapKeys { AksjonspunktDefinisjon.fraKode(it.key) }
                .filter { (k, v) -> !k.erAutopunkt() && v == "OPPR"}
        }

        fun inneholderEtAktivtAksjonspunktMedKoden(liste: Map<String, String>, def: AksjonspunktDefinisjon): Boolean {
            val definisjon = liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> entry.key }
                .find { kode -> kode == def.kode }
            return definisjon != null
        }

        fun inneholderEtAvAktivtAksjonspunktMedKoden(
            liste: Map<String, String>,
            def: List<AksjonspunktDefinisjon>
        ): Boolean {
            val definisjon = liste.filter { entry -> entry.value == "OPPR" }
                .map { entry -> entry.key }
                .find { kode -> def.map { it.kode }.contains(kode) }
            return definisjon != null
        }

        fun inneholderEtAvAksjonspunktUavheningStatusMedKoden(
            liste: Map<String, String>,
            def: List<AksjonspunktDefinisjon>
        ): Boolean {
            val definisjon = liste
                .map { entry -> entry.key }
                .find { kode -> def.map { it.kode }.contains(kode) }
            return definisjon != null
        }

        fun inneholderEtInaktivtAksjonspunktMedKoden(liste: Map<String, String>, def: AksjonspunktDefinisjon): Boolean {
            val definisjon = liste.filter { entry -> entry.value != "OPPR" }
                .map { entry -> entry.key }
                .find { kode -> kode == def.kode }
            return definisjon != null
        }

        fun inneholderEtAvInaktivtAksjonspunkterMedKoder(
            liste: Map<String, String>,
            def: List<AksjonspunktDefinisjon>
        ): Boolean {
            val definisjon = liste.filter { entry -> entry.value != "OPPR" }
                .map { entry -> entry.key }
                .find { kode -> def.map { it.kode }.contains(kode) }
            return definisjon != null
        }

        fun finnAlleAksjonspunkter(): List<AksjonspunktMock> {
            val fraK9Sak = AksjonspunktDefinisjon.values().filter { it.kode != null }.map {
                AksjonspunktMock(
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

        fun aksjonspunkterFraPunsj(): MutableList<AksjonspunktMock> {
            return mutableListOf(
                AksjonspunktMock("PUNSJ", "Punsj oppgave", "MANU", "", "", null, false),
                AksjonspunktMock("UTLØPT", "Utløpt oppgave", "MANU", "", "", null, false),
                AksjonspunktMock("MER_INFORMASJON", "Venter på informasjon", "MANU", "", "", null, false)
            )
        }

    }
}
