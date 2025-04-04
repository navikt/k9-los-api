package no.nav.k9.los.nyoppgavestyring.driftsmelding

import java.time.LocalDateTime
import java.util.*

class DriftsmeldingTjeneste(
    private val driftsmeldingRepository: DriftsmeldingRepository
) {

    fun hentDriftsmeldinger(): List<DriftsmeldingDto> {
        return driftsmeldingRepository.hentAlle().sortedByDescending { it.aktivert }
    }

    fun slettDriftsmelding(id: UUID) {
        return driftsmeldingRepository.slett(id)
    }

    fun leggTilDriftsmelding(melding: String): DriftsmeldingDto {
        val driftsmelding = DriftsmeldingDto(
                UUID.randomUUID(),
                melding,
                LocalDateTime.now(),
                false,
                null
        )
        driftsmeldingRepository.lagreDriftsmelding(driftsmelding)

        return driftsmelding
    }

    fun toggleDriftsmelding(driftsmelding: DriftsmeldingSwitch) {
        driftsmeldingRepository.setDriftsmelding(driftsmelding, if (driftsmelding.aktiv) LocalDateTime.now() else null)
    }
}
