package no.nav.k9.los

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.runMigration
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
            connectionTimeout = 10000
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

const val TØM_DATA_SQL = """
            truncate 
                driftsmeldinger,
                oppgavefelt_verdi,
                saksbehandler,
                siste_oppgaver,
                OPPGAVEKO_SAKSBEHANDLER,
                OPPGAVEKO_V3,
                RESERVASJON_V3,
                RESERVASJON_V3_ENDRING,
                OPPGAVE_V3,
                oppgave_v3_sendt_dvh,
                OPPGAVE_PEP_CACHE,
                kodeverk,
                kodeverk_verdi,
                omrade,
                oppgavetype,
                oppgavefelt,
                oppgavefelt_verdi_part,
                oppgavefelt_verdi,
                oppgavefelt_verdi_aktiv,
                oppgave_v3_part,
                oppgave_id_part,
                oppgave_v3,
                oppgave_v3_aktiv,
                feltdefinisjon,
                uttrekk,
                lagret_sok,
                event,
                event_historikkvask_bestilt,
                event_nokkel;
                
            ALTER SEQUENCE saksbehandler_id_seq restart
        """

abstract class AbstractPostgresTest {
    companion object {
        private val postgresContainer = KPostgreSQLContainer("postgres:16-alpine")
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
            it.createStatement().execute(TØM_DATA_SQL)
        }

    }
}