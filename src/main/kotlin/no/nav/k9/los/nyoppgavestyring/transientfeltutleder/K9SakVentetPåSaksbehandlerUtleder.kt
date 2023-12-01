package no.nav.k9.los.nyoppgavestyring.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakVentetPåSaksbehandlerUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidSaksbehandlerForTidligereVersjoner")
    ),
    løpendetidfelter = listOf(
        OmrådeOgKode("K9", "avventerSaksbehandler")
    ))