package no.nav.k9.los.infrastruktur.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

class TransactionalManager(
    private val dataSource: DataSource
) {
    fun <A> transactionContext(operasjon: context(TransactionalSession) () -> A): A =
        using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx -> context(tx) { operasjon() } }
        }

    fun <A> transaction(operation: (TransactionalSession) -> A): A {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction {
                operation(it)
            }
        }
    }

    suspend fun <A> transactionSuspend(operation: suspend (TransactionalSession) -> A): A {
        return withContext(Dispatchers.IO) {
            using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
                session.transaction {
                    runBlocking {
                        operation(it)
                    }
                }
            }
        }
    }
}
