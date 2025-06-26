package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav

enum class AksjonspunktDefinisjonK9Tilbake(
    val kode: String,
    val totrinn: Boolean = false,
    val erAutopunkt: Boolean = false
) {
    VURDER_TILBAKEKREVING("5002", totrinn = true),
    VURDER_FORELDELSE("5003", totrinn = true),
    FORESLÅ_VEDTAK("5004", totrinn = true),
    FATTE_VEDTAK("5005"),
    AVKLAR_VERGE("5030"),
    VENT_PÅ_BRUKERTILBAKEMELDING("7001", erAutopunkt = true),
    VENT_PÅ_TILBAKEKREVINGSGRUNNLAG("7002", erAutopunkt = true),
    AVKLART_FAKTA_FEILUTBETALING("7003", totrinn = true),

    // kun brukes for å sende data til fplos når behandling venter på grunnlaget etter fristen
    VURDER_HENLEGGELSE_MANGLER_KRAVGRUNNLAG("8001");

    companion object {
        fun fraKode(kode: String): AksjonspunktDefinisjonK9Tilbake {
            return entries.first { it.kode == kode }
        }
    }

}