package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType

enum class DagensTallHovedgruppe(val navn: String, val ytelser: List<FagsakYtelseType>?) {
    ALLE("Alle ytelser", null),
    OMSORGSPENGER("Omsorgspenger", listOf(FagsakYtelseType.OMSORGSPENGER)),
    OMSORGSDAGER("Omsorgsdager", listOf(FagsakYtelseType.OMSORGSDAGER, FagsakYtelseType.OMSORGSPENGER_KS, FagsakYtelseType.OMSORGSPENGER_AO, FagsakYtelseType.OMSORGSPENGER_MA)),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn", listOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)),
    PPN("Pleiepenger i livets sluttfase", listOf(FagsakYtelseType.PPN));
}
