package no.nav.k9.los.driftsmelding

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.time.LocalDateTime
import java.util.*

class DriftsmeldingTjeneste(
    private val driftsmeldingRepository: DriftsmeldingRepository
) {

    fun hentDriftsmeldinger(område: Områder): List<DriftsmeldingDto> {
        return driftsmeldingRepository.hentAlle(område).sortedByDescending { it.aktivert }
    }

    fun slettDriftsmelding(område: Områder, id: UUID) {
        return driftsmeldingRepository.slett(område, id)
    }

    fun leggTilDriftsmelding(område: Områder, melding: String): DriftsmeldingDto {
        val driftsmelding = DriftsmeldingDto(
                UUID.randomUUID(),
                melding,
                LocalDateTime.now(),
                false,
                null
        )
        driftsmeldingRepository.lagreDriftsmelding(område, driftsmelding)

        return driftsmelding
    }

    fun toggleDriftsmelding(område: Områder, driftsmelding: DriftsmeldingSwitch) {
        driftsmeldingRepository.setDriftsmelding(område, driftsmelding, if (driftsmelding.aktiv) LocalDateTime.now() else null)
    }
}
