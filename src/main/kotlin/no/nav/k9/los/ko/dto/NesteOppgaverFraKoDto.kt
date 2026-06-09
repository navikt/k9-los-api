package no.nav.k9.los.ko.dto

data class NesteOppgaverFraKoDto(
    // feltkode -> visningsnavn
    val kolonner: Map<String, String>,
    // for hver rad: feltkode -> feltverdi
    val rader: List<Map<String, String>>,
)