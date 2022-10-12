package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import io.ktor.http.ParametersBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class DataeksportApisKtTest {

    @Test
    fun `Saksbehandler er ikke et velgbart felt grunnet personvern`() {
        val parametre = ParametersBuilder()
            .apply { append("filtre", "${VelgbartHistorikkfelt.DATO},${VelgbartHistorikkfelt.SAKSBEHANDLER}") }
            .build()

        assertFailsWith<UnsupportedOperationException> {
            hentFiltre(parametre)
        }
    }
}