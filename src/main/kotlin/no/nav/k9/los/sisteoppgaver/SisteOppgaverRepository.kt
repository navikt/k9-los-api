package no.nav.k9.los.sisteoppgaver

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.db.EksternOppgaveId
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import javax.sql.DataSource

class SisteOppgaverRepository(
    private val dataSource: DataSource,
) {
    fun hentSisteOppgaver(
        tx: TransactionalSession,
        brukerIdent: String,
        område: Områder,
    ): List<EksternOppgaveId> {
        return tx.run(
            queryOf(
                """
                    SELECT so.oppgave_ekstern_id, o.ekstern_id AS omrade
                    FROM siste_oppgaver so
                    JOIN oppgavetype ot ON ot.id = so.oppgavetype_id
                    JOIN omrade o ON o.id = ot.omrade_id
                    WHERE so.bruker_ident = :bruker_ident AND o.ekstern_id = :omrade
                    ORDER BY so.tidspunkt DESC
                    LIMIT 10
                """.trimIndent(),
                mapOf("bruker_ident" to brukerIdent, "omrade" to område.eksternId)
            ).map { row ->
                EksternOppgaveId(Områder.fraEksternId(row.string("omrade")), row.string("oppgave_ekstern_id"))
            }.asList
        )
    }

    fun lagreSisteOppgave(
        tx: TransactionalSession,
        brukerIdent: String,
        oppgaveNøkkel: OppgaveNøkkelDto,
    ) {
        tx.run(
            queryOf(
                """
                    INSERT INTO siste_oppgaver (oppgave_ekstern_id, oppgavetype_id, bruker_ident, tidspunkt)
                    VALUES (:oppgaveEksternId, (
                        SELECT ot.id FROM oppgavetype ot JOIN omrade o ON o.id = ot.omrade_id
                        WHERE ot.ekstern_id = :oppgavetype AND o.ekstern_id = :omrade
                    ), :bruker_ident, localtimestamp)
                    ON CONFLICT (oppgave_ekstern_id, oppgavetype_id, bruker_ident)
                    DO UPDATE SET tidspunkt = localtimestamp
                """.trimIndent(),
                mapOf(
                    "oppgaveEksternId" to oppgaveNøkkel.oppgaveEksternId,
                    "oppgavetype" to oppgaveNøkkel.oppgaveTypeEksternId,
                    "omrade" to oppgaveNøkkel.områdeEksternId.eksternId,
                    "bruker_ident" to brukerIdent,
                )
            ).asUpdate
        )
    }

    fun ryddOppForBrukerIdent(
        tx: TransactionalSession,
        brukerIdent: String,
        område: Områder,
    ) {
        tx.run(
            queryOf(
                """
                    DELETE FROM siste_oppgaver
                    WHERE ctid IN (
                        SELECT so.ctid
                        FROM siste_oppgaver so
                        JOIN oppgavetype ot ON ot.id = so.oppgavetype_id
                        JOIN omrade o ON o.id = ot.omrade_id
                        WHERE so.bruker_ident = :bruker_ident AND o.ekstern_id = :omrade
                        ORDER BY so.tidspunkt DESC
                        OFFSET 10
                    )
                """.trimIndent(),
                mapOf(
                    "bruker_ident" to brukerIdent,
                    "omrade" to område.eksternId,
                )
            ).asUpdate
        )
    }
}
