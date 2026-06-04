package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.db.OmrådeOgKode

class K9SakVentetPåSaksbehandlerUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidSaksbehandlerForTidligereVersjoner")
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerSaksbehandler")
    ))