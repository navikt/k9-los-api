package no.nav.k9.los.infrastruktur.db

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

private val log = LoggerFactory.getLogger("DatabaseMigration")

fun main() {
    val configuration = Configuration(HoconApplicationConfig(ConfigFactory.load()))
    check(configuration.koinProfile() != KoinProfile.LOCAL) {
        "Databasejobben skal bruke Vault-konfigurasjon"
    }

    val (antallMigrert, tidsbruk) = measureTimedValue {
        dataSourceFromVault(configuration, Role.Admin).use { dataSource ->
            val initSql = "SET ROLE \"${configuration.databaseName()}-${Role.Admin}\""
            runMigration(dataSource, initSql)
        }
    }

    log.info("Databasemigrering fullført. Antall migrert={}, tidsbruk={}", antallMigrert, tidsbruk)
}
