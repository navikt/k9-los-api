package no.nav.k9.los.ktor.jackson

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.KotlinInvalidNullException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.path
import io.ktor.util.rootCause
import io.ktor.utils.io.InternalAPI
import no.nav.k9.los.ktor.core.DefaultProblemDetails
import no.nav.k9.los.ktor.core.ParameterType
import no.nav.k9.los.ktor.core.ValidationProblemDetails
import no.nav.k9.los.ktor.core.Violation
import no.nav.k9.los.ktor.core.respondProblemDetails
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.jackson.JacksonStatusPages")

@OptIn(InternalAPI::class)
fun StatusPagesConfig.JacksonStatusPages() {

    exception<JsonMappingException> { call, cause ->
        if (cause.cause is IllegalArgumentException) {
            call.respondProblemDetails(
                DefaultProblemDetails(
                    title = "IllegalArgumentException",
                    status = 400,
                    detail = "${cause.cause as IllegalArgumentException} -> ${cause.path}"
                ),
                logger,
                cause
            )
        } else {
            val violations = mutableSetOf<Violation>()
            cause.path.filter { it.fieldName != null }.forEach {
                violations.add(
                    Violation(
                        parameterType = ParameterType.ENTITY,
                        parameterName = it.fieldName,
                        reason = "Må være satt.",
                        invalidValue = null

                    )
                )
            }

            val problemDetails = ValidationProblemDetails(violations)
            call.respondProblemDetails(problemDetails, logger, cause)
        }
    }

    exception<JsonProcessingException> { call, cause ->

        val problemDetails = DefaultProblemDetails(
            title = "invalid-json-entity",
            status = 400,
            detail = "Request entityen inneholder ugyldig JSON."
        )
        call.respondProblemDetails(problemDetails, logger, cause)
    }

    exception { call: ApplicationCall, cause: BadRequestException ->
        val problemDetails = when (val rootCause = cause.rootCause) {
            is KotlinInvalidNullException -> {
                ValidationProblemDetails(
                    setOf(
                        Violation(
                            parameterName = rootCause.kotlinPropertyName ?: "ukjent",
                            parameterType = ParameterType.ENTITY,
                            reason = "Må være satt.",
                            invalidValue = null
                        )
                    )
                )
            }

            else -> {
                DefaultProblemDetails(
                    title = "invalid-request-parameters",
                    status = 400,
                    detail = "Requesten inneholder ugyldige parametere.",
                    instance = URI(call.request.path())
                )
            }
        }

        call.respondProblemDetails(problemDetails, logger, cause)
    }
}
