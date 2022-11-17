package no.nav.k9.los.tjenester.mock

import no.nav.k9.los.domene.modell.AksjonspunktDefWrapper

class AksjonspunkterMock {
    fun aksjonspunkter(): List<AksjonspunktMock> {
        return AksjonspunktDefWrapper.finnAlleAksjonspunkter()
    }
}

data class AksjonspunktMock(
    val kode: String,
    val navn: String,
    val aksjonspunktype: String,
    val behandlingsstegtype: String,
    val plassering: String,
    val vilkårtype: String?,
    val totrinn: Boolean,
    var antall: Int = 0
)
