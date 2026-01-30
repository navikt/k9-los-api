package io.kotest.provided

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import no.nav.k9.los.TØM_DATA_SQL
import no.nav.k9.los.buildAndTestConfig
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.runMigration
import no.nav.k9.los.tjenester.mock.localSetup.getKoin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource


class ProjectConfig : AbstractProjectConfig() {
    override var extensions: List<Extension> = listOf(DbCleanupListener)

    override suspend fun beforeProject() {
        KotestPostgresTestContainer.instance.start()

        val container = KotestPostgresTestContainer.instance
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
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

        val dataSource = HikariDataSource(hikariConfig)

        runMigration(dataSource)

        startKoin {
            modules(
                buildAndTestConfig(
                    dataSource = dataSource
                )
            )
        }

        getKoin().get<OmrådeSetup>().setup()
    }

    override suspend fun afterProject() {
        stopKoin()
        KotestPostgresTestContainer.instance.stop()
    }
}

object DbCleanupListener : TestListener {
    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        val ds: DataSource = getKoin().get()
        cleanupTables(ds)
    }
}

fun cleanupTables(dataSource: DataSource) {
    dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            stmt.execute(TØM_DATA_SQL)
        }
    }
}

object KotestPostgresTestContainer {
    val instance: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("los-kotest")
            withUsername("testuser")
            withPassword("testpass")
            // Do NOT start here, start explicitly in beforeProject()
        }
    }
}