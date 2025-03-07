package no.nav.k9.los.db

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import java.util.*
import javax.sql.DataSource
import kotlin.time.measureTimedValue

enum class Role {
    Admin, User, ReadOnly;

    override fun toString() = name.lowercase(Locale.getDefault())
}

fun getDataSource(configuration: Configuration): HikariDataSource =
    if (configuration.koinProfile() == KoinProfile.LOCAL) {
        HikariDataSource(configuration.hikariConfig())
    } else {
        dataSourceFromVault(configuration, Role.User)
    }

fun dataSourceFromVault(hikariConfig: Configuration, role: Role): HikariDataSource =
    HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfig.hikariConfig(),
        hikariConfig.getVaultDbPath(),
        "${hikariConfig.databaseName()}-$role"
    )

fun Application.migrate(configuration: Configuration) {
    log.info("Migrerer database")
    val (antallMigrert, tidsbruk) = measureTimedValue {
        if (configuration.koinProfile() == KoinProfile.LOCAL) {
            runMigration(HikariDataSource(configuration.hikariConfig()))
        } else {
            runMigration(
                dataSourceFromVault(configuration, Role.Admin),
                "SET ROLE \"${configuration.databaseName()}-${Role.Admin}\""
            )
        }
    }
    log.info("Migrert database, antallMigrert={} tidsbruk={}", antallMigrert, tidsbruk)
}

fun runMigration(dataSource: DataSource, initSql: String? = null): Int {
    return Flyway.configure()
        .locations("migreringer/")
        .dataSource(dataSource)
        .initSql(initSql)
        .load()
        .migrate()
        .migrationsExecuted
}
