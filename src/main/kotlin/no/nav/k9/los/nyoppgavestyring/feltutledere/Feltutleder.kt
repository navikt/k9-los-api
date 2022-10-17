package no.nav.k9.los.nyoppgavestyring.feltutledere

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3

interface Feltutleder {
    val påkrevdeFelter: HashMap<String, String> // Verdi må her være en klasse med statiske felter
    fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3, tx: TransactionalSession): OppgaveV3
}
