package no.nav.k9.los.nyoppgavestyring.sisteoppgaver

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import javax.sql.DataSource

class SisteOppgaverRepository(
    private val dataSource: DataSource,
) {
    fun hentSisteOppgaver(
        tx: TransactionalSession,
        brukerIdent: String,
    ): List<EksternOppgaveId> {
        return tx.run(
            queryOf(
                """
                    SELECT oppgave_ekstern_id
                    FROM siste_oppgaver
                    WHERE bruker_ident = :bruker_ident
                    ORDER BY tidspunkt DESC
                    LIMIT 10
                """.trimIndent(),
                mapOf("bruker_ident" to brukerIdent)
            ).map { row ->
                EksternOppgaveId("K9", row.string("oppgave_ekstern_id"))
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
                    VALUES (:oppgaveEksternId, (select id from oppgavetype where ekstern_id = :oppgavetype), :bruker_ident, NOW())
                    ON CONFLICT (oppgave_ekstern_id, oppgavetype_id, bruker_ident)
                    DO UPDATE SET tidspunkt = NOW()
                """.trimIndent(),
                mapOf(
                    "oppgaveEksternId" to oppgaveNøkkel.oppgaveEksternId,
                    "oppgavetype" to oppgaveNøkkel.oppgaveTypeEksternId,
                    "bruker_ident" to brukerIdent,
                )
            ).asUpdate
        )
    }

    fun ryddOppForBrukerIdent(
        tx: TransactionalSession,
        brukerIdent: String,
    ) {
        tx.run(
            queryOf(
                """
                    DELETE FROM siste_oppgaver
                    WHERE ctid IN (
                        SELECT ctid
                        FROM siste_oppgaver
                        WHERE bruker_ident = :bruker_ident
                        ORDER BY tidspunkt DESC
                        OFFSET 10
                    )
                """.trimIndent(),
                mapOf(
                    "bruker_ident" to brukerIdent,
                )
            ).asUpdate
        )
    }
}
