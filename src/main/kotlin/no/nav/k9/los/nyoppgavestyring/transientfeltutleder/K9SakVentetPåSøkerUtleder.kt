package no.nav.k9.los.nyoppgavestyring.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakVentetPåSøkerUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidSøkerForTidligereVersjoner")
    ),
    løpendetidfelter = listOf(
        OmrådeOgKode("K9", "avventerSøker")
    ))