package no.nav.k9.los.domene.repository

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingDto
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingRepository
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class DriftsmeldingRepositoryTest : AbstractK9LosIntegrationTest() {

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
