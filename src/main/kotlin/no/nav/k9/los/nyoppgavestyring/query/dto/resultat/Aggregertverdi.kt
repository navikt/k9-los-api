package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

data class Aggregertverdi(
    val type: String,
    val område: String?,
    val kode: String?,
    val verdi: Number,
)
