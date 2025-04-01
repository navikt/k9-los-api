package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType

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
