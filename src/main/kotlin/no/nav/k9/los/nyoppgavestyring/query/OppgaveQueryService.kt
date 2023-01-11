package no.nav.k9.los.nyoppgavestyring.query

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.koin.java.KoinJavaComponent.inject
import javax.sql.DataSource

class OppgaveQueryService() {
    val datasource by inject<DataSource>(DataSource::class.java)
    val oppgaveQueryRepository by inject<OppgaveQueryRepository>(OppgaveQueryRepository::class.java)
    val oppgaveRepository by inject<OppgaveRepository>(OppgaveRepository::class.java)

    fun hentAlleFelter(): Oppgavefelter {
        return oppgaveQueryRepository.hentAlleFelter();
    }

    fun queryForOppgaveId(oppgaveQuery: OppgaveQuery): List<Long> {
        return oppgaveQueryRepository.query(oppgaveQuery);
    }

    fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuery): List<Oppgaverad> {
        val oppgaver: List<Long> = oppgaveQueryRepository.query(tx, oppgaveQuery)
        if (oppgaveQuery.select.isEmpty()) {
            return listOf(Oppgaverad(listOf(Oppgavefeltverdi(null, "Antall", oppgaver.size))))
        }

        return oppgaver.map {
            val oppgave = oppgaveRepository.hentOppgaveForId(tx, it)
            val felter = toOppgavefeltverdier(oppgaveQuery, oppgave)
            // TODO: pluss på felter som er direkte på oppgaven.

            Oppgaverad(felter)
        }
    }

    private fun toOppgavefeltverdier(
        oppgaveQuery: OppgaveQuery,
        oppgave: Oppgave
    ) = oppgaveQuery.select.map {
        if (it is EnkelSelectFelt) {
            Oppgavefeltverdi(
                it.område,
                it.kode,
                oppgave.hentVerdiEllerListe(requireNotNull(it.område), it.kode))
        } else {
            // TODO: Støtt aggregerte felter:
            Oppgavefeltverdi("", "", "")
        }
    }

    fun query(oppgaveQuery: OppgaveQuery): List<Oppgaverad> {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> query(tx, oppgaveQuery) }
        }
    }
}