package no.nav.k9.los

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.k9.los.infrastruktur.db.runMigration
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
            do $$
            declare
                truncate_sql text;
            begin
                select
                    'truncate table ' || string_agg(format('%I.%I', schemaname, tablename), ', ') || ' restart identity cascade'
                into truncate_sql
                from pg_tables
                where schemaname = 'public'
                  and tablename <> 'flyway_schema_history';

                if truncate_sql is not null then
                    execute truncate_sql;
                end if;
            end
            $$;
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