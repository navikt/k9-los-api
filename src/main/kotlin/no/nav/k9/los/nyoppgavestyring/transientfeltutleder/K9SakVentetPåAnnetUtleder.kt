package no.nav.k9.los.nyoppgavestyring.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakVentetPåAnnetUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidAnnetForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerAnnet")
    ))