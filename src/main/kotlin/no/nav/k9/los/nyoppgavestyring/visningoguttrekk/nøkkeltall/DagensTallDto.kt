package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import java.time.LocalDateTime

sealed class DagensTallResponse {
    data class Suksess(
        val oppdatertTidspunkt: LocalDateTime,
        val hovedgrupper: List<KodeOgNavn>,
        val undergrupper: List<KodeOgNavn>,
        val tall: List<DagensTallDto>
    ) : DagensTallResponse()

    data class Feil(val feilmelding: String): DagensTallResponse()
}

data class KodeOgNavn(
    val kode: String,
    val navn: String
)

data class DagensTallDto(
    val hovedgruppe: DagensTallHovedgruppe,
    val undergruppe: DagensTallUndergruppe,
    val nyeIDag: Long,
    val ferdigstilteIDag: Long,
    val nyeSiste7Dager: Long,
    val ferdigstilteSiste7Dager: Long,
)

enum class DagensTallHovedgruppe(val navn: String) {
    ALLE("Alle ytelser"),
    OMSORGSPENGER("Omsorgspenger"),
    OMSORGSDAGER("Omsorgsdager"),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn"),
    PPN("Pleiepenger i livets sluttfase"),
    PUNSJ("Punsj");

    companion object {
        fun fraFagsakYtelseType(fagsakYtelseType: FagsakYtelseType): DagensTallHovedgruppe {
            return when (fagsakYtelseType) {
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> PLEIEPENGER_SYKT_BARN
                FagsakYtelseType.OMSORGSPENGER -> OMSORGSPENGER
                FagsakYtelseType.OMSORGSDAGER -> OMSORGSDAGER
                FagsakYtelseType.PPN -> PPN
                else -> throw IllegalArgumentException("Støtter ikke fagsakYtelseType=$fagsakYtelseType")
            }
        }
    }
}

enum class DagensTallUndergruppe(val navn: String) {
    TOTALT("Totalt"),
    FORSTEGANGSSOKNAD("Førstegangsbehandling"),
    REVURDERING("Revurdering");

    companion object {
        fun fraBehandlingType(behandlingType: BehandlingType): DagensTallUndergruppe {
            return when (behandlingType) {
                BehandlingType.FORSTEGANGSSOKNAD -> FORSTEGANGSSOKNAD
                BehandlingType.REVURDERING -> REVURDERING
                else -> throw IllegalArgumentException("Støtter ikke behandlingstype=$behandlingType")
            }
        }
    }
}