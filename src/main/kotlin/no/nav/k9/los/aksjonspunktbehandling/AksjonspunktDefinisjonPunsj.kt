package no.nav.k9.los.aksjonspunktbehandling

data class AksjonspunktDefinisjonPunsj (val kode: String, val erAutopunkt:Boolean=false) {
    companion object {

        val PUNSJ: AksjonspunktDefinisjonPunsj = AksjonspunktDefinisjonPunsj("PUNSJ")
        val UTLØPT: AksjonspunktDefinisjonPunsj = AksjonspunktDefinisjonPunsj("UTLØPT")
        val MER_INFORMASJON: AksjonspunktDefinisjonPunsj = AksjonspunktDefinisjonPunsj("MER_INFORMASJON", erAutopunkt = true)

        fun alle() : List<AksjonspunktDefinisjonPunsj> {
            return listOf(PUNSJ, UTLØPT, MER_INFORMASJON)
        }
    }


}