package no.nav.k9.los.søkeboks

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeDispatcher
import no.nav.k9.los.søkeboks.k9.K9Oppgavesøk
import no.nav.k9.los.søkeboks.aktivitetspenger.AktivitetspengerOppgavesøk
import no.nav.k9.los.søkeboks.ung.UngOppgavesøk

class Oppgavesøkere(
    k9: K9Oppgavesøk,
    aktivitetspenger: AktivitetspengerOppgavesøk,
    ungdomsprogramytelsen: UngOppgavesøk,
) : OmrådeDispatcher<Oppgavesøk>(k9, aktivitetspenger, ungdomsprogramytelsen)
