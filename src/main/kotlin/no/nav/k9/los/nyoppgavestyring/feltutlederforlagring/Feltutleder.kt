package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave.OppgaveV3

interface Feltutleder {
    fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi?

    fun hentFeltutledernavn(): String {
        return this::class.java.canonicalName
    }
}
