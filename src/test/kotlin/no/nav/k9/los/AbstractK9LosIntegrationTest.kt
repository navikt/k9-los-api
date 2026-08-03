package no.nav.k9.los

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest

abstract class AbstractK9LosIntegrationTest: AbstractPostgresTest(), KoinTest {

    @BeforeEach
    fun startKoin() {
        stopKoin()
        startKoin { modules(buildAndTestConfig(dataSource)) }
    }

    @AfterEach
    fun stoppKoin() {
        stopKoin()
    }

}