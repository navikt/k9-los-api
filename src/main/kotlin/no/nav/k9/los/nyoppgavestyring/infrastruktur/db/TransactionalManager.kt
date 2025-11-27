package no.nav.k9.los.nyoppgavestyring.infrastruktur.db

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
    fun <A> transaction(operation: (TransactionalSession) -> A): A {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction {
                operation(it)
            }
        }
    }

    suspend fun <A> transactionSuspend(queryTimeout: Int? = null, operation: suspend (TransactionalSession) -> A): A {
        return withContext(Dispatchers.IO) {
            using(sessionOf(dataSource, returnGeneratedKey = true, queryTimeout = queryTimeout)) { session ->
                session.transaction {
                    runBlocking {
                        operation(it)
                    }
                }
            }
        }
    }
}