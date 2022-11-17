package no.nav.k9.los.nyoppgavestyring.feltutledere

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3

interface Feltutleder {
    fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi?

    fun hentFeltutledernavn(): String {
        return this::class.java.canonicalName
    }
}
