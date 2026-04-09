package no.nav.k9.los.nyoppgavestyring.uttrekk

data class UttrekkResultatRespons(
    val kolonner: List<String>,
    val rader: List<UttrekkRad>,
    val totaltAntall: Int,
    val offset: Int,
    val limit: Int?
)