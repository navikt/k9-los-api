package no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.db.OmrådeOgKode

class K9SakOppgavesaksbehandlingstidUtleder: LøpendeDurationTransientFeltutleder(
    durationfelter = listOf(
        OmrådeOgKode(Områder.K9, "akkumulertVentetidSaksbehandlerForTidligereVersjoner"),
        OmrådeOgKode(Områder.K9, "akkumulertVentetidTekniskFeilForTidligereVersjoner"),
        OmrådeOgKode(Områder.K9, "akkumulertVentetidArbeidsgiverForTidligereVersjoner"),
        OmrådeOgKode(Områder.K9, "akkumulertVentetidAnnetForTidligereVersjoner"),
    ),
    løpendeTidHvisTrueFelter = listOf(
        OmrådeOgKode(Områder.K9, "avventerSaksbehandler"),
        OmrådeOgKode(Områder.K9, "avventerTekniskFeil"),
        OmrådeOgKode(Områder.K9, "avventerArbeidsgiver"),
        OmrådeOgKode(Områder.K9, "avventerAnnet"),
    )) {
}