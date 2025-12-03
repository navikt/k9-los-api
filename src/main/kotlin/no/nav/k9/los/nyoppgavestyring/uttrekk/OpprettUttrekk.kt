package no.nav.k9.los.nyoppgavestyring.uttrekk

data class OpprettUttrekk(
    val lagretSokId: Long,
    val kjoreplan: String?,
    val typeKjoring: TypeKjøring
)