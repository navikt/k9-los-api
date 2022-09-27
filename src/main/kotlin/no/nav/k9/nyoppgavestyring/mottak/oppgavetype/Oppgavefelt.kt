package no.nav.k9.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon

class Oppgavefelt(
    val id : Long? = null,
    val feltDefinisjon: Feltdefinisjon,
    val visPåOppgave: Boolean,
    val påkrevd: Boolean
)