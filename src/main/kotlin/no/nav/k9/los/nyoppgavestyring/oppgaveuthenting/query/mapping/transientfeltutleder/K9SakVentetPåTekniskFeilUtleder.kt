package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.db.OmrådeOgKode

class K9SakVentetPåTekniskFeilUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidTekniskFeilForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerTekniskFeil")
    ))