package no.nav.k9.los.infrastruktur.abac.tilganger

import no.nav.k9.los.infrastruktur.idtoken.IIdToken

/**
 * Sammenligner tilgangene Los beregner selv med tilgangene sif-abac-pdp ville gitt, som forberedelse
 * til å la sif-abac-pdp overta. Observasjonen skjer utenfor request-løpet: den skal aldri kunne
 * påvirke svaret eller svartiden til kalleren.
 */
internal interface PdpTilgangsskygge {
    fun observer(idToken: IIdToken, autoritative: Tilganger)
}
