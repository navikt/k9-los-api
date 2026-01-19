package no.nav.k9.los.nyoppgavestyring.uttrekk

data class OpprettUttrekk(
    val lagretSokId: Long,
    val typeKjoring: TypeKjøring,
    val tittel: String = "",
    val limit: Int?,
    val offset: Int?
)