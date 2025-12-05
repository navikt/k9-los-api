package no.nav.k9.los.nyoppgavestyring.uttrekk

data class OpprettUttrekk(
    val lagretSokId: Long,
    val typeKjoring: TypeKjøring,
    val timeout: Int
)