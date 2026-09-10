package no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.db.OmrådeOgKode

class K9SakVentetPåAnnetUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode(Områder.K9, "akkumulertVentetidAnnetForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode(Områder.K9, "avventerAnnet")
    ))