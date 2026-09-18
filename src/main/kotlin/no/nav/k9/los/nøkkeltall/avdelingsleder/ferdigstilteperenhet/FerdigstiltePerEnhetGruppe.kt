package no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet

import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9FagsakYtelseType

enum class FerdigstiltePerEnhetGruppe(val navn: String, val ytelser: List<K9FagsakYtelseType>?) {
    ALLE("Alle ytelser", null),
    OMSORGSPENGER("Omsorgspenger", listOf(K9FagsakYtelseType.OMSORGSPENGER)),
    OMSORGSDAGER("Omsorgsdager", listOf(K9FagsakYtelseType.OMSORGSDAGER, K9FagsakYtelseType.OMSORGSPENGER_KS, K9FagsakYtelseType.OMSORGSPENGER_MA, K9FagsakYtelseType.OMSORGSPENGER_AO)),
    OPPLÆRINGSPENGER("Opplæringspenger", listOf(K9FagsakYtelseType.OLP)),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn", listOf(K9FagsakYtelseType.PLEIEPENGER_SYKT_BARN)),
    PPN("Pleiepenger i livets sluttfase", listOf(K9FagsakYtelseType.PPN)),
    PUNSJ("Punsj", null);
}