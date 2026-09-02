package no.nav.k9.los.infrastruktur.db

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
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
            HikariDataSource(configuration.hikariConfig()).use { dataSource ->
                runMigration(dataSource)
            }
        } else {
            dataSourceFromVault(configuration, Role.Admin).use { dataSource ->
                runMigration(
                    dataSource,
                    "SET ROLE \"${configuration.databaseName()}-${Role.Admin}\""
                )
            }
        }
    }
    log.info("Migrert database, antallMigrert={} tidsbruk={}", antallMigrert, tidsbruk)
}

fun runMigration(dataSource: DataSource, initSql: String? = null): Int {
    val configuration = Flyway.configure()
        .locations("migreringer/")
        .dataSource(dataSource)
        .initSql(initSql)

    // CREATE INDEX CONCURRENTLY kan vente på Flyways egen transaksjonelle advisory lock.
    // Session-lock bevarer eksklusiv migreringskjøring uten å slå av transaksjoner for vanlige migreringer.
    configuration.pluginRegister
        .getExact(PostgreSQLConfigurationExtension::class.java)
        .setTransactionalLock(false)

    return configuration.load()
        .migrate()
        .migrationsExecuted
}
