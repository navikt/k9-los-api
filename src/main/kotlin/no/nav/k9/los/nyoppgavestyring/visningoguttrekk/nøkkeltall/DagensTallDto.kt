package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType

data class DagensTallDto(
    val dagensTallType: DagensTallType,
    val behandlingType: BehandlingType?,
    val nyeIDag: Long,
    val ferdigstilteIDag: Long,
    val nyeSiste7Dager: Long,
    val ferdigstilteSiste7Dager: Long,
)

enum class DagensTallType(val navn: String) {
    OMSORGSPENGER("Omsorgspenger"),
    OMSORGSDAGER("Omsorgsdager"),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn"),
    PPN("Pleiepenger i livets sluttfase"),
    PUNSJ("Punsj");

    companion object {
        fun fraFagsakYtelseType(fagsakYtelseType: FagsakYtelseType) : DagensTallType {
            return when (fagsakYtelseType) {
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> DagensTallType.PLEIEPENGER_SYKT_BARN
                FagsakYtelseType.OMSORGSPENGER -> DagensTallType.OMSORGSPENGER
                FagsakYtelseType.OMSORGSDAGER -> DagensTallType.OMSORGSDAGER
                FagsakYtelseType.PPN -> DagensTallType.PPN
                else -> throw IllegalArgumentException()
            }
        }
    }
}