package no.nav.k9.los.nyoppgavestyring.uthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.uthenting.query.db.OmrådeOgKode

class K9SakVentetPåTekniskFeilUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidTekniskFeilForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerTekniskFeil")
    ))