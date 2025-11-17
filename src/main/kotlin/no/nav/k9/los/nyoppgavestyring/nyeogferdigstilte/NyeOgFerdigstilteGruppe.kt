package no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte

enum class NyeOgFerdigstilteGruppe(val navn: String) {
    ALLE("Alle ytelser"),
    OMSORGSPENGER("Omsorgspenger"),
    OMSORGSDAGER("Omsorgsdager"),
    OPPLÆRINGSPENGER("Opplæringspenger"),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn"),
    PPN("Pleiepenger i livets sluttfase"),
    PUNSJ("Punsj");
}