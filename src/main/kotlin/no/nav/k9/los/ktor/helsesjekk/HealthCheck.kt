package no.nav.k9.los.ktor.helsesjekk

sealed class Result(open val resultat: Map<String, Any?>)

class Healthy(navn: String, resultat: Any) : Result(mapOf("result" to resultat, "name" to navn))

class UnHealthy(navn: String, resultat: Any) : Result(mapOf("result" to resultat, "name" to navn))

fun interface HealthCheck {
    suspend fun check(): Result
}
