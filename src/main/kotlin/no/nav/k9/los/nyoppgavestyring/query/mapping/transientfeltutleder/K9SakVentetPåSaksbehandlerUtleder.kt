package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakVentetPåSaksbehandlerUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidSaksbehandlerForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerSaksbehandler")
    ))