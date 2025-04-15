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
        saksbehandlerIdent: String,
    ): List<EksternOppgaveId> {
        return tx.run(
            queryOf(
                """
                    SELECT oppgave_ekstern_id
                    FROM siste_oppgaver
                    WHERE saksbehandler = :saksbehandlerIdent
                    ORDER BY tidspunkt DESC
                    LIMIT 10
                """.trimIndent(),
                mapOf("saksbehandlerIdent" to saksbehandlerIdent)
            ).map { row ->
                EksternOppgaveId("K9", row.string("oppgave_ekstern_id"))
            }.asList
        )
    }

    fun lagreSisteOppgave(
        tx: TransactionalSession,
        saksbehandlerIdent: String,
        oppgaveNøkkel: OppgaveNøkkelDto,
    ) {
        tx.run(
            queryOf(
                """
                    INSERT INTO siste_oppgaver (oppgave_ekstern_id, oppgavetype_id, saksbehandler, tidspunkt)
                    VALUES (:oppgaveEksternId, (select id from oppgavetype where ekstern_id = :oppgavetype), :saksbehandlerIdent, NOW())
                    ON CONFLICT (oppgave_ekstern_id, oppgavetype_id, saksbehandler)
                    DO UPDATE SET tidspunkt = NOW()
                """.trimIndent(),
                mapOf(
                    "oppgaveEksternId" to oppgaveNøkkel.oppgaveEksternId,
                    "oppgavetype" to oppgaveNøkkel.oppgaveTypeEksternId,
                    "saksbehandlerIdent" to saksbehandlerIdent,
                )
            ).asUpdate
        )
    }

    fun ryddOppForSaksbehandler(
        tx: TransactionalSession,
        saksbehandlerIdent: String,
    ) {
        tx.run(
            queryOf(
                """
                    DELETE FROM siste_oppgaver
                    WHERE ctid IN (
                        SELECT ctid
                        FROM siste_oppgaver
                        WHERE saksbehandler = :saksbehandler
                        ORDER BY tidspunkt DESC
                        OFFSET 10
                    )
                """.trimIndent(),
                mapOf(
                    "saksbehandler" to saksbehandlerIdent,
                )
            ).asUpdate
        )
    }
}
