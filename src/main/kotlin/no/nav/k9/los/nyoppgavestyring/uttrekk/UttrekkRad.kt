package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon

data class UttrekkRad(
    val id: String,
    val kolonner: List<UttrekkKolonneverdi>,
)

data class UttrekkKolonneverdi(
    val kode: String? = null,
    val område: String? = null,
    val funksjon: Aggregeringsfunksjon? = null,
    val verdi: Any? = null,
) {
    fun csvKolonnenavn(): String {
        val funksjonPrefix = funksjon?.name?.lowercase()
        return when {
            funksjonPrefix != null && kode != null -> "${funksjonPrefix}_$kode"
            funksjonPrefix != null -> funksjonPrefix
            kode != null -> kode
            else -> ""
        }
    }
}
