package no.nav.k9.los.nyoppgavestyring.infrastruktur.utils

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

object OpentelemetrySpanUtil {
    val tracer: Tracer = GlobalOpenTelemetry.getTracer("application")

    fun <T> span(name: String, attributter: Map<String, String> = emptyMap(), operasjon: (() -> T)): T {

        var builder = tracer.spanBuilder(name)
        for ((attributtnavn, attributtverdi) in attributter) {
            builder = builder.setAttribute(attributtnavn, attributtverdi)
        }
        val span: Span = builder.startSpan()

        try {
            return span.makeCurrent().use { operasjon.invoke() }
        } catch (throwable: Throwable) {
            span.setStatus(StatusCode.ERROR, throwable.javaClass.name)
            span.recordException(throwable)
            throw throwable
        } finally {
            span.end()
        }
    }

    suspend fun <T> spanSuspend(
        name: String,
        attributter: Map<String, String> = emptyMap(),
        operasjon: (suspend () -> T)
    ): T {

        var builder = tracer.spanBuilder(name)
        for ((attributtnavn, attributtverdi) in attributter) {
            builder = builder.setAttribute(attributtnavn, attributtverdi)
        }
        val span: Span = builder.startSpan()

        return span.makeCurrent().use {
            try {
                operasjon.invoke()
            } catch (throwable: Throwable) {
                span.setStatus(StatusCode.ERROR, throwable.javaClass.name)
                span.recordException(throwable)
                throw throwable
            } finally {
                span.end()
            }
        }
    }
}