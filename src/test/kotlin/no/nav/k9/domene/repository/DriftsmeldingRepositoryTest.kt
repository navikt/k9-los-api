package no.nav.k9.domene.repository

import no.nav.k9.AbstractPostgresTest
import no.nav.k9.buildAndTestConfig
import no.nav.k9.tjenester.driftsmeldinger.DriftsmeldingDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class DriftsmeldingRepositoryTest: KoinTest, AbstractPostgresTest() {

        @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource))
    }

    @Test
    fun skalLagreDriftsmeldingOgHenteDenIgjen() {

        val driftsmeldingRepository = get<DriftsmeldingRepository>()

        val driftsmelding =
            DriftsmeldingDto(
                    UUID.randomUUID(),
                    "Driftsmelding",
            LocalDateTime.now(),
            false,
            null)
        driftsmeldingRepository.lagreDriftsmelding(driftsmelding)

        val alle = driftsmeldingRepository.hentAlle()
        assertEquals(driftsmelding.id, alle[0].id)
        assertEquals(driftsmelding.melding, alle[0].melding)
        assertEquals(driftsmelding.aktiv, alle[0].aktiv)

        driftsmeldingRepository.slett(driftsmelding.id)
        val ingen = driftsmeldingRepository.hentAlle()
        assertEquals(0, ingen.size)
    }

}
