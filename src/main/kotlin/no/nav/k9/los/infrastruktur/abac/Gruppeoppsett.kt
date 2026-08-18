package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter
import java.util.*

internal class Gruppeoppsett(
    k9: GrupperForOmråde = GrupperForOmråde(
        saksbehandler = uuidFraMiljø("K9_SAKSBEHANDLER_GRUPPE_ID"),
        veileder = uuidFraMiljø("K9_VEILEDER_GRUPPE_ID"),
        oppgavestyrer = uuidFraMiljø("K9_OPPGAVESTYRER_GRUPPE_ID"),
        drift = uuidFraMiljø("K9_DRIFT_GRUPPE_ID"),
        kode6 = uuidFraMiljø("K9_KODE6_GRUPPE_ID"),
    ),
    ung: GrupperForOmråde = GrupperForOmråde(
        saksbehandler = uuidFraMiljø("UNG_SAKSBEHANDLER_GRUPPE_ID"),
        veileder = uuidFraMiljø("UNG_VEILEDER_GRUPPE_ID"),
        oppgavestyrer = uuidFraMiljø("UNG_OPPGAVESTYRER_GRUPPE_ID"),
        drift = uuidFraMiljø("UNG_DRIFT_GRUPPE_ID"),
        kode6 = uuidFraMiljø("UNG_KODE6_GRUPPE_ID"),
    ),
) : OmrådeRuter<GrupperForOmråde>(k9, ung) {
    companion object {
        private fun uuidFraMiljø(navn: String): UUID? =
            System.getenv(navn)?.takeIf(String::isNotBlank)?.let(UUID::fromString)
    }
}