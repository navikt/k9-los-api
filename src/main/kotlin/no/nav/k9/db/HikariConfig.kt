package no.nav.k9.db


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import no.nav.k9.Configuration

fun createHikariConfig(jdbcUrl: String, username: String? = null, password: String? = null) =
    HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        maximumPoolSize = 12
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 10000
        maxLifetime = 30001
        driverClassName = "org.postgresql.Driver"
        username?.let { this.username = it }
        password?.let { this.password = it }
    }

fun Application.hikariConfig(hikariConfig: Configuration): HikariDataSource {
    migrate(hikariConfig)
    return getDataSource(hikariConfig)
}