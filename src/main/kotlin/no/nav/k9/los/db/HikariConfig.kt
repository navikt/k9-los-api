package no.nav.k9.los.db


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import io.ktor.server.application.*
import no.nav.k9.los.Configuration

fun createHikariConfig(jdbcUrl: String, username: String? = null, password: String? = null) =
    HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        maximumPoolSize = 6
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 10000
        maxLifetime = 30001
        driverClassName = "org.postgresql.Driver"
        username?.let { this.username = it }
        password?.let { this.password = it }
        setMetricsTrackerFactory(PrometheusMetricsTrackerFactory())
    }

fun Application.hikariConfig(hikariConfig: Configuration): HikariDataSource {
    //migrate(hikariConfig)
    return getDataSource(hikariConfig)
}