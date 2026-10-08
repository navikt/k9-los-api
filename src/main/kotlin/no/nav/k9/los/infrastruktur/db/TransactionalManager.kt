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
    fun <A> transaction(operation: (TransactionalSession) -> A): A {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.transaction {
                operation(it)
            }
        }
    }

    suspend fun <A> transactionSuspend(operation: suspend (TransactionalSession) -> A): A {
        return withContext(Dispatchers.IO) {
            val context = coroutineContext
            using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
                session.transaction {
                    runBlocking(context) {
                        operation(it)
                    }
                }
            }
        }
    }
}

fun <A> TransactionalSession.medSavepoint(operation: () -> A): A {
    val connection = this.connection.underlying
    val savepoint = connection.setSavepoint()
    try {
        val resultat = operation()
        connection.releaseSavepoint(savepoint)
        return resultat
    } catch (e: Throwable) {
        connection.rollback(savepoint)
        throw e
    }
}

