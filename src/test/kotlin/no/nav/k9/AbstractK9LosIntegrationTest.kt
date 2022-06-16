package no.nav.k9

import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension

abstract class AbstractK9LosIntegrationTest: AbstractPostgresTest(), KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource))
    }

}