package no.nav.k9.los.tjenester.mock


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
