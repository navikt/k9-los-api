package no.nav.k9.aksjonspunktbehandling

data class AksjonspunktDefinisjonK9Tilbake (val kode: String) {
    companion object {

        val SEND_VARSEL: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5001")
        val VURDER_TILBAKEKREVING: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5002")
        val VURDER_FORELDELSE: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5003")
        val FORESLÅ_VEDTAK: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5004")
        val FATTE_VEDTAK: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5005")
        val VENT_PÅ_BRUKERTILBAKEMELDING: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("7001")
        val VENT_PÅ_TILBAKEKREVINGSGRUNNLAG: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("7002")
        val AVKLART_FAKTA_FEILUTBETALING: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("7003")
        val AVKLAR_VERGE: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("5030")

        // kun brukes for å sende data til fplos når behandling venter på grunnlaget etter fristen
        val VURDER_HENLEGGELSE_MANGLER_KRAVGRUNNLAG: AksjonspunktDefinisjonK9Tilbake = AksjonspunktDefinisjonK9Tilbake("8001")
    }

}