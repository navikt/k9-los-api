package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache

import kotliquery.TransactionalSession
import kotliquery.queryOf

class TestRepository {

    fun hentEksternIdForAlleOppgaver(tx: TransactionalSession): List<String> {
        return tx.run(
            queryOf(
                """
                select ov.ekstern_id 
                from oppgave_v3 ov
            """.trimIndent(),
            ).map { row -> row.string("ekstern_id") }.asList
        )
    }
}