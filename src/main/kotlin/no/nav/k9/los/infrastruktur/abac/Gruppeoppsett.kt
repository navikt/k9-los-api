package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter
import java.util.*

internal class Gruppeoppsett(
    k9: K9Grupper = K9Grupper(
        saksbehandler = uuidFraMiljø("K9_SAKSBEHANDLER_GRUPPE_ID"),
        veileder = uuidFraMiljø("K9_VEILEDER_GRUPPE_ID"),
        oppgavestyrer = uuidFraMiljø("K9_OPPGAVESTYRER_GRUPPE_ID"),
        kode6 = uuidFraMiljø("K9_KODE6_GRUPPE_ID"),
    ),
    aktivitetspenger: AktivitetspengerGrupper = AktivitetspengerGrupper(
        saksbehandlerLokalkontor = uuidFraMiljø("AKTIVITETSPENGER_SAKSBEHANDLER_LOKALKONTOR_GRUPPE_ID"),
        saksbehandlerNay = uuidFraMiljø("AKTIVITETSPENGER_SAKSBEHANDLER_NAY_GRUPPE_ID"),
        oppgavestyrer = uuidFraMiljø("AKTIVITETSPENGER_OPPGAVESTYRER_GRUPPE_ID"),
        kode6 = uuidFraMiljø("AKTIVITETSPENGER_KODE6_GRUPPE_ID"),
    ),
    // Driftsmeldinger er globale for hele Los, og drift-gruppen er derfor ikke områdespesifikk.
    // Gruppen har beholdt K9-prefiks av historiske årsaker, men gjelder alle områder.
    val drift: UUID? = uuidFraMiljø("K9_DRIFT_GRUPPE_ID"),
) : OmrådeRuter<GrupperForOmråde>(k9, aktivitetspenger) {
    companion object {
        private fun uuidFraMiljø(navn: String): UUID? =
            System.getenv(navn)?.takeIf(String::isNotBlank)?.let(UUID::fromString)
    }
}
