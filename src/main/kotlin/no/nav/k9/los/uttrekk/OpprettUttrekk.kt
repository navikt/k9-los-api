package no.nav.k9.los.uttrekk

data class OpprettUttrekk(
    val lagretSokId: Long,
    val tittel: String = "",
    val limit: Int?,
    val offset: Int?
)