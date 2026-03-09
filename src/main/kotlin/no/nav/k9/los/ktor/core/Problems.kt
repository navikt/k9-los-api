package no.nav.k9.los.ktor.core

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.json.JSONObject
import org.slf4j.Logger
import java.net.URI

/*
    https://tools.ietf.org/html/rfc7807#section-3
 */

interface Problem {
    fun getProblemDetails(): ProblemDetails
}

class Throwblem : Throwable, Problem {
    private val occurredProblemDetails: ProblemDetails

    constructor(problemDetails: ProblemDetails) : super(problemDetails.asMap().toString()) {
        this.occurredProblemDetails = problemDetails
    }

    constructor(problemDetails: ProblemDetails, throwable: Throwable) : super(
        problemDetails.asMap().toString(),
        throwable
    ) {
        this.occurredProblemDetails = problemDetails
    }

    override fun getProblemDetails(): ProblemDetails = occurredProblemDetails
}

interface ProblemDetails {
    val title: String
    val type: URI
    val status: Int
    val detail: String
    val instance: URI
    fun asMap(): Map<String, Any>
}

suspend fun ApplicationCall.respondProblemDetails(
    problemDetails: ProblemDetails,
    logger: Logger,
    cause: Throwable?
) {
    val json = JSONObject(problemDetails.asMap()).toString()
    if (cause == null) {
        logger.info("ProblemDetails='$json'")
    } else {
        logger.warn("ProblemDetails='$json'", cause)
    }

    attributes.put(AttributeKey("problem-details"), json)

    respondText(
        text = json,
        contentType = ContentType.Application.ProblemJson,
        status = HttpStatusCode.fromValue(problemDetails.status)
    )
}

open class DefaultProblemDetails(
    override val title: String,
    override val type: URI = URI("/problem-details/$title"),
    override val status: Int,
    override val detail: String,
    override val instance: URI = URI("about:blank")
) : ProblemDetails {
    override fun asMap(): Map<String, Any> {
        return mapOf(
            Pair("type", type.toString()),
            Pair("title", title),
            Pair("status", status),
            Pair("detail", detail),
            Pair("instance", instance.toString())
        )
    }
}

enum class ParameterType {
    QUERY,
    PATH,
    HEADER,
    ENTITY,
    FORM
}

data class Violation(
    val parameterName: String,
    val parameterType: ParameterType,
    val reason: String,
    val invalidValue: Any? = null
)

data class ValidationProblemDetails(
    val violations: Set<Violation>

) : DefaultProblemDetails(
    title = "invalid-request-parameters",
    status = 400,
    detail = "Requesten inneholder ugyldige paramtere."
) {
    override fun asMap(): Map<String, Any> {
        val invalidParametersList: MutableList<Map<String, Any?>> = mutableListOf()
        violations.forEach {
            invalidParametersList.add(
                mapOf(
                    Pair("type", it.parameterType.name.lowercase()),
                    Pair("name", it.parameterName),
                    Pair("reason", it.reason),
                    Pair("invalid_value", it.invalidValue)
                )
            )
        }
        return super.asMap().toMutableMap().apply {
            put("invalid_parameters", invalidParametersList)
        }.toMap()
    }
}
