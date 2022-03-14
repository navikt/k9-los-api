package no.nav.k9.domene.lager.oppgave.v2

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

class TransactionalManager(
    private val dataSource: DataSource
) {
    fun <A> transaction(operation: (TransactionalSession) -> A): A {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction {
                operation(it)
            }
        }
    }
}