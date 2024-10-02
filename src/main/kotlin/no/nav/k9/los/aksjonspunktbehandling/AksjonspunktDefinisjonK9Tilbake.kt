package no.nav.k9.los.aksjonspunktbehandling

data class AksjonspunktDefinisjonK9Tilbake (val kode: String, val totrinn:Boolean=false, val erAutopunkt:Boolean=false) {
    companion object {

        val VURDER_TILBAKEKREVING: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5002", totrinn = true)
        val VURDER_FORELDELSE: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5003", totrinn = true)
        val FORESLÅ_VEDTAK: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5004", totrinn = true)
        val FATTE_VEDTAK: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5005")
        val AVKLAR_VERGE: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5030")
        val VENT_PÅ_BRUKERTILBAKEMELDING: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("7001", erAutopunkt = true)
        val VENT_PÅ_TILBAKEKREVINGSGRUNNLAG: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("7002", erAutopunkt = true)
        val AVKLART_FAKTA_FEILUTBETALING: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("7003", totrinn = true)

        // kun brukes for å sende data til fplos når behandling venter på grunnlaget etter fristen
        val VURDER_HENLEGGELSE_MANGLER_KRAVGRUNNLAG: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("8001")

        fun alle() : List<AksjonspunktDefinisjonK9Tilbake> {
            return listOf(VURDER_TILBAKEKREVING, VURDER_FORELDELSE, FORESLÅ_VEDTAK, FATTE_VEDTAK, AVKLAR_VERGE, VENT_PÅ_BRUKERTILBAKEMELDING, VENT_PÅ_TILBAKEKREVINGSGRUNNLAG, AVKLART_FAKTA_FEILUTBETALING, VURDER_HENLEGGELSE_MANGLER_KRAVGRUNNLAG)
        }

        fun fraKode(kode : String) : AksjonspunktDefinisjonK9Tilbake {
            val match = alle().filter { it.kode == kode }
            if (match.isNotEmpty()) {
                return match[0]
            }
            throw IllegalArgumentException("Har ikke AksjonspunktDefinisjon for tilbake med kode $kode");
        }
    }

}