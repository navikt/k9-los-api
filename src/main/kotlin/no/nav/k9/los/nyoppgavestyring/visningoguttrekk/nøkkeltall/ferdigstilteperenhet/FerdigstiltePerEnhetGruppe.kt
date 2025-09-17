package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType

enum class FerdigstiltePerEnhetGruppe(val navn: String, val ytelser: List<FagsakYtelseType>?) {
    ALLE("Alle ytelser", null),
    OMSORGSPENGER("Omsorgspenger", listOf(FagsakYtelseType.OMSORGSPENGER)),
    OMSORGSDAGER("Omsorgsdager", listOf(FagsakYtelseType.OMSORGSDAGER, FagsakYtelseType.OMSORGSPENGER_KS, FagsakYtelseType.OMSORGSPENGER_MA, FagsakYtelseType.OMSORGSPENGER_AO)),
    OPPLÆRINGSPENGER("Opplæringspenger", listOf(FagsakYtelseType.OLP)),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn", listOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)),
    PPN("Pleiepenger i livets sluttfase", listOf(FagsakYtelseType.PPN)),
    PUNSJ("Punsj", null);
}