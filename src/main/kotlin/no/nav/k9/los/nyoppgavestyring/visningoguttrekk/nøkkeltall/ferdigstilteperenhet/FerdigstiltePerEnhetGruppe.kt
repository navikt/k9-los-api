package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import no.nav.k9.los.domene.modell.FagsakYtelseType

enum class FerdigstiltePerEnhetGruppe(val navn: String) {
    ALLE("Alle ytelser"),
    OMSORGSPENGER("Omsorgspenger"),
    OMSORGSDAGER("Omsorgsdager"),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn"),
    PPN("Pleiepenger i livets sluttfase"),
    PUNSJ("Punsj");

    companion object {
        fun fraFagsakYtelse(ytelse: FagsakYtelseType): FerdigstiltePerEnhetGruppe {
            return when (ytelse) {
                FagsakYtelseType.OMSORGSPENGER -> OMSORGSPENGER
                FagsakYtelseType.OMSORGSDAGER -> OMSORGSDAGER
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN -> PLEIEPENGER_SYKT_BARN
                FagsakYtelseType.PPN -> PPN
                else -> throw IllegalArgumentException("FagsakYtelseType $ytelse er ikke støttet")
            }
        }
    }
}