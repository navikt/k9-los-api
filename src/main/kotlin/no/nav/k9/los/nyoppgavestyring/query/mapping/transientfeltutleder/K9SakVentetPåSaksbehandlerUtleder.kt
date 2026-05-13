package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakVentetPåSaksbehandlerUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", K9FeltIder.AKKUMULERT_VENTETID_SAKSBEHANDLER_FOR_TIDLIGERE_VERSJONER)
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", K9FeltIder.AVVENTER_SAKSBEHANDLER)
    ))