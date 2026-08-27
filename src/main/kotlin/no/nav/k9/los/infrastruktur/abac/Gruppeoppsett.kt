package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter
import java.util.*

/**
 * Tilganger for innlogget bruker hentes fra sif-abac-pdp (se SifAbacPdpTilgangerKlient),
 * ikke fra gruppe-claims på tokenet. Det eneste som gjenstår her er kode6-gruppe-idene,
 * som brukes ved oppslag på _andre_ saksbehandlere via Azure Graph
 * (PepClient.harSaksbehandlerTilgangTilKode6).
 *
 * TODO: flytt også dette oppslaget til sif-abac-pdp når PDP tilbyr tilgangsoppslag på ident.
 */
internal class Gruppeoppsett(
    k9: Kode6ForOmråde = Kode6ForOmråde(uuidFraMiljø("K9_KODE6_GRUPPE_ID")),
    aktivitetspenger: Kode6ForOmråde = Kode6ForOmråde(uuidFraMiljø("AKTIVITETSPENGER_KODE6_GRUPPE_ID")),
) : OmrådeRuter<Kode6ForOmråde>(k9, aktivitetspenger) {
    companion object {
        private fun uuidFraMiljø(navn: String): UUID? =
            System.getenv(navn)?.takeIf(String::isNotBlank)?.let(UUID::fromString)
    }
}

internal data class Kode6ForOmråde(val kode6: UUID?)
