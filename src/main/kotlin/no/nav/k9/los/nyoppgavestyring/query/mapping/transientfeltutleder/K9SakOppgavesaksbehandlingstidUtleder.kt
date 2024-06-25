package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode

class K9SakOppgavesaksbehandlingstidUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode("K9", "akkumulertVentetidSaksbehandlerForTidligereVersjoner"),
        OmrådeOgKode("K9", "akkumulertVentetidTekniskFeilForTidligereVersjoner"),
        OmrådeOgKode("K9", "akkumulertVentetidArbeidsgiverForTidligereVersjoner"),
        OmrådeOgKode("K9", "akkumulertVentetidAnnetForTidligereVersjoner"),
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode("K9", "avventerSaksbehandler"),
        OmrådeOgKode("K9", "avventerTekniskFeil"),
        OmrådeOgKode("K9", "avventerArbeidsgiver"),
        OmrådeOgKode("K9", "avventerAnnet"),
    )) {
}