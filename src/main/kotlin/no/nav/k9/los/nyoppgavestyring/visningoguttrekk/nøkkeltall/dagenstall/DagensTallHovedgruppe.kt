package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

enum class DagensTallHovedgruppe(val navn: String) {
    ALLE("Alle ytelser"),
    OMSORGSPENGER("Omsorgspenger"),
    OMSORGSDAGER("Omsorgsdager"),
    OPPLÆRINGSPENGER("Opplæringspenger"),
    PLEIEPENGER_SYKT_BARN("Pleiepenger sykt barn"),
    PPN("Pleiepenger i livets sluttfase");
}
