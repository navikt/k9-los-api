package no.nav.k9.los.infrastruktur.abac.tilganger

internal sealed interface PdpResultat {
    data class Suksess(val tilganger: Tilganger) : PdpResultat
    data class Feil(val type: String, val status: Int? = null) : PdpResultat
}
