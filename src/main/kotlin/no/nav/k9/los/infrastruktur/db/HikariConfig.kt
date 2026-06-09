package no.nav.k9.los.infrastruktur.db


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import io.ktor.server.application.*
import no.nav.k9.los.Configuration

const val DB_POOL_SIZE = 6

/**
 * Maks antall parallelle coroutines/tråder som bruker DB-tilkoblinger.
 * Satt til [DB_POOL_SIZE] - 2 for å alltid ha ledig kapasitet til
 * ad-hoc-spørringer og andre deler av applikasjonen.
 */
val DB_AWARE_PARALLELISM = (DB_POOL_SIZE - 2).coerceAtLeast(1)

fun createHikariConfig(jdbcUrl: String, username: String? = null, password: String? = null) =
    HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        maximumPoolSize = DB_POOL_SIZE
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 10000
        driverClassName = "org.postgresql.Driver"
        username?.let { this.username = it }
        password?.let { this.password = it }
        setMetricsTrackerFactory(PrometheusMetricsTrackerFactory())
    }

fun Application.hikariConfig(hikariConfig: Configuration): HikariDataSource {
    migrate(hikariConfig)
    return getDataSource(hikariConfig)
}