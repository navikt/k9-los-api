package no.nav.k9.los

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigurationTest {
    @Test
    fun `Nais krever Vault-konfigurasjon`() {
        listOf("dev-fss", "prod-fss").forEach { cluster ->
            val feil = assertFailsWith<IllegalStateException> { konfigurasjon(cluster) }
            assertContains(feil.message.orEmpty(), "DB_VAULT_MOUNTPATH")
        }
    }

    @Test
    fun `ukjent cluster får ikke lokal profil`() {
        assertFailsWith<IllegalStateException> { konfigurasjon("ukjent-fss") }
    }

    @Test
    fun `velger profil for kjente miljøer`() {
        assertEquals(KoinProfile.PREPROD, konfigurasjon("dev-fss", "postgresql/preprod-fss/").koinProfile)
        assertEquals(KoinProfile.PROD, konfigurasjon("prod-fss", "postgresql/prod-fss/").koinProfile)
        assertEquals(KoinProfile.VERDIKJEDE, konfigurasjon("LOCALHOST-FSS").koinProfile)
        assertEquals(KoinProfile.LOCAL, konfigurasjon("").koinProfile)
    }

    private fun konfigurasjon(cluster: String, vaultMountpath: String = "") = Configuration(
        HoconApplicationConfig(
            ConfigFactory.parseMap(
                mapOf(
                    "nav.auth.clients" to emptyList<Any>(),
                    "nav.clustername" to cluster,
                    "nav.db.vault_mountpath" to vaultMountpath,
                )
            )
        )
    )
}
