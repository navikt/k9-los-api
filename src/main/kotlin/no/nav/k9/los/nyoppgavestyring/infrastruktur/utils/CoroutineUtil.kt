package no.nav.k9.los.nyoppgavestyring.infrastruktur.utils

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

val EXCEPTION_HANDLER = CoroutineExceptionHandler { _, exception ->
        val log = LoggerFactory.getLogger("CoroutineExceptionHandler")
        log.error("Uncaught exception in coroutine", exception)
    }

fun ExecutorService.asCoroutineDispatcherWithErrorHandling(): CoroutineContext {
    return asCoroutineDispatcher() + EXCEPTION_HANDLER
}