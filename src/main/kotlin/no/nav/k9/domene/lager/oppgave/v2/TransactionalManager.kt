package no.nav.k9.domene.lager.oppgave.v2

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

class TransactionalManager(
    private val dataSource: DataSource
) {
    fun <A> transaction(operation: (TransactionalSession) -> A): A {
        val session = sessionOf(dataSource, returnGeneratedKey = true)
        return session.transaction {
            operation(it)
        }
    }
}