package no.nav.k9.los.nyoppgavestyring.søkeboks

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus

data class SøkRequest(val søkeord: String, val oppgavestatus: List<Oppgavestatus>)
