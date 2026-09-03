package no.nav.k9.los.infrastruktur.db

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

private val log = LoggerFactory.getLogger("DatabaseMigration")

fun main(args: Array<String>) {
    val operation = args.singleOrNull() ?: error("Forventet operasjon: migrate eller verify")
    val configuration = Configuration(HoconApplicationConfig(ConfigFactory.load()))
    check(configuration.koinProfile() != KoinProfile.LOCAL) {
        "Databasejobben skal bruke Vault-konfigurasjon"
    }

    val (resultat, tidsbruk) = measureTimedValue {
        dataSourceFromVault(configuration, Role.Admin).use { dataSource ->
            val initSql = "SET ROLE \"${configuration.databaseName()}-${Role.Admin}\""
            when (operation) {
                "migrate" -> "${runMigration(dataSource, initSql)} migreringer kjørt"
                "verify" -> {
                    verifyMigrationHistory(dataSource, initSql)
                    "Flyway-historikk verifisert"
                }
                else -> error("Ukjent operasjon: $operation")
            }
        }
    }

    log.info("Databaseoperasjon fullført: {}. Tidsbruk={}", resultat, tidsbruk)
}
