package no.nav.k9.los.domene.repository

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.driftsmelding.DriftsmeldingDto
import no.nav.k9.los.driftsmelding.DriftsmeldingRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
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
        driftsmeldingRepository.lagreDriftsmelding(Områder.K9, driftsmelding)

        val alle = driftsmeldingRepository.hentAlle(Områder.K9)
        assertEquals(driftsmelding.id, alle[0].id)
        assertEquals(driftsmelding.melding, alle[0].melding)
        assertEquals(driftsmelding.aktiv, alle[0].aktiv)

        driftsmeldingRepository.slett(Områder.K9, driftsmelding.id)
        val ingen = driftsmeldingRepository.hentAlle(Områder.K9)
        assertEquals(0, ingen.size)
    }

}
