package no.nav.k9

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.k9.db.runMigration
import org.junit.After
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource


class TestDataSource {

    fun dataSource(kPostgreSQLContainer: KPostgreSQLContainer): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = kPostgreSQLContainer.jdbcUrl
        config.username = kPostgreSQLContainer.username
        config.password = kPostgreSQLContainer.password
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        return HikariDataSource(config)
    }

}


// Hack needed because testcontainers use of generics confuses Kotlin
class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

abstract class AbstractPostgresTest {
    companion object {
        private val postgresContainer = KPostgreSQLContainer("postgres:11.1")
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

    @After
    fun tømDB() {
        dataSource.connection.use {
            it.createStatement().execute("""
            truncate 
                behandling_prosess_events_k9,
                behandling_prosess_events_k9_punsj,
                behandling_prosess_events_tilbake,
                driftsmeldinger,
                ferdigstilte_behandlinger,
                nye_og_ferdigstilte,
                oppgave,
                oppgaveko,
                reservasjon,
                saksbehandler,
                siste_behandlinger
        """)
        }

    }
}