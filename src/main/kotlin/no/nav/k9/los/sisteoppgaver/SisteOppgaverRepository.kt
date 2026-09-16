package no.nav.k9.los.sisteoppgaver

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.query.db.EksternOppgaveId
import javax.sql.DataSource

class SisteOppgaverRepository(
    private val dataSource: DataSource,
) {
    fun hentSisteOppgaver(
        område: Områder,
        brukerIdent: String,
        tx: TransactionalSession,
    ): List<EksternOppgaveId> {
        return tx.run(
            queryOf(
                """
                    SELECT oppgave_ekstern_id
                    FROM siste_oppgaver
                    WHERE bruker_ident = :bruker_ident
                      AND omrade_id = (select id from omrade o where o.ekstern_id = :omrade_ekstern_id)
                    ORDER BY tidspunkt DESC
                    LIMIT 10
                """.trimIndent(),
                mapOf(
                    "bruker_ident" to brukerIdent,
                    "omrade_ekstern_id" to område.eksternId
                )
            ).map { row ->
                EksternOppgaveId(område, row.string("oppgave_ekstern_id"))
            }.asList
        )
    }

    fun lagreSisteOppgave(
        område: Områder,
        brukerIdent: String,
        oppgaveNøkkel: OppgaveNøkkelDto,
        tx: TransactionalSession,
    ) {
        tx.run(
            queryOf(
                """
                    INSERT INTO siste_oppgaver (oppgave_ekstern_id, oppgavetype_id, bruker_ident, tidspunkt, omrade_id)
                    VALUES (:oppgaveEksternId, (select ot.id from oppgavetype ot where ot.ekstern_id = :oppgavetype), :bruker_ident, localtimestamp, (select o.id from omrade o where o.ekstern_id = :omrade_ekstern_id))
                    ON CONFLICT (oppgave_ekstern_id, oppgavetype_id, bruker_ident)
                    DO UPDATE SET tidspunkt = localtimestamp
                """.trimIndent(),
                mapOf(
                    "oppgaveEksternId" to oppgaveNøkkel.oppgaveEksternId,
                    "oppgavetype" to oppgaveNøkkel.oppgaveTypeEksternId,
                    "bruker_ident" to brukerIdent,
                    "omrade_ekstern_id" to område.eksternId
                )
            ).asUpdate
        )
    }

    fun ryddOppForBrukerIdent(
        område: Områder,
        brukerIdent: String,
        tx: TransactionalSession,
    ) {
        tx.run(
            queryOf(
                """
                    DELETE FROM siste_oppgaver
                    WHERE ctid IN (
                        SELECT ctid
                        FROM siste_oppgaver
                        WHERE bruker_ident = :bruker_ident AND omrade_id = (select o.id from omrade o where o.ekstern_id = :omrade_ekstern_id)
                        ORDER BY tidspunkt DESC
                        OFFSET 10
                    )
                """.trimIndent(),
                mapOf(
                    "bruker_ident" to brukerIdent,
                    "omrade_ekstern_id" to område.eksternId
                )
            ).asUpdate
        )
    }
}
