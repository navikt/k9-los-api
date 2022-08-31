package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjon

class Oppgavefelt(
    val id : Long? = null,
    val feltDefinisjon: Feltdefinisjon,
    val visPåOppgave: Boolean,
    val påkrevd: Boolean
)