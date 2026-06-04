package no.nav.k9.los.nyoppgavestyring.uthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.uthenting.query.db.OmrådeOgKode

class K9SakVentetPåArbeidsgiverUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidArbeidsgiverForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerArbeidsgiver")
    ))