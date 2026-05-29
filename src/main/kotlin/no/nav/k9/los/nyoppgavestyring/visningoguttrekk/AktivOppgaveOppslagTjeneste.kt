package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession

interface AktivOppgaveOppslagTjeneste {
    fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String): Oppgave
    fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String, tx: TransactionalSession): Oppgave
    fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetypeEksternId: String): Oppgave?
    fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetypeEksternId: String, tx: TransactionalSession): Oppgave?
}
