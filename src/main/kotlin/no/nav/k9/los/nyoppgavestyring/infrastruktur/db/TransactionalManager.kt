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

    suspend fun <T> suspendingTransaction(
        block: suspend (TransactionalSession) -> T
    ): T = withContext(Dispatchers.IO) {
        using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                runBlocking {
                    block(tx)
                }
            }
        }
    }
}