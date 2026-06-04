package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.nyoppgavestyring.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.oppgavemottak.OppgaveV3

interface Feltutleder {
    fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi?

    fun hentFeltutledernavn(): String {
        return this::class.java.canonicalName
    }
}
