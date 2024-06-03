package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakVentetPåSøkerUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidSøkerForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerSøker")
    ))