package no.nav.k9.los

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.k9.los.db.runMigration
import org.junit.jupiter.api.AfterEach
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource


class TestDataSource {

    fun dataSource(kPostgreSQLContainer: KPostgreSQLContainer): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = kPostgreSQLContainer.jdbcUrl
            username = kPostgreSQLContainer.username
            password = kPostgreSQLContainer.password
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 5000
            maxLifetime = 30001
            driverClassName = "org.postgresql.Driver"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        return HikariDataSource(config)
    }

}


// Hack needed because testcontainers use of generics confuses Kotlin
class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

abstract class AbstractPostgresTest {
    companion object {
        private val postgresContainer = KPostgreSQLContainer("postgres:12")
            .withDatabaseName("my-db")
            .withUsername("foo")
            .withPassword("secret")
            .withReuse(true)
            .also { it.start()  }

        @JvmStatic
        protected val dataSource = TestDataSource().dataSource(postgresContainer)

        init {
            runMigration(dataSource)
        }

    }

    @AfterEach
    fun tømDB() {
        dataSource.connection.use {
            it.createStatement().execute("""
            truncate 
                behandling_prosess_events_k9,
                behandling_prosess_events_k9_historikkvask_ferdig,
                behandling_prosess_events_klage_historikkvask_ferdig,
                behandling_prosess_events_k9_punsj,
                behandling_prosess_events_tilbake,
                driftsmeldinger,
                ferdigstilte_behandlinger,
                nye_og_ferdigstilte,
                oppgave,
                oppgavefelt_verdi,
                oppgaveko,
                reservasjon,
                reservasjon_v3_endring,
                reservasjon_v3,
                saksbehandler,
                siste_behandlinger,
                OPPGAVEKO_SAKSBEHANDLER,
                OPPGAVEKO_V3,
                OPPGAVE_V3,
                OPPGAVE_PEP_CACHE,
                oppgave_v3_sendt_dvh
        """)
        }

    }
}