package no.nav.k9.tjenester.mock

import no.nav.k9.domene.modell.AksjonspunktDefWrapper

class Aksjonspunkter {
    fun aksjonspunkter(): List<Aksjonspunkt> {
        return AksjonspunktDefWrapper.finnAlleAksjonspunkter()
    }
}

data class Aksjonspunkt(
    val kode: String,
    val navn: String,
    val aksjonspunktype: String,
    val behandlingsstegtype: String,
    val plassering: String,
    val vilkårtype: String?,
    val totrinn: Boolean,
    var antall: Int = 0
)
