package no.nav.k9.los.nyoppgavestyring.uthenting

data class Oppgavefelt(
    val eksternId: String,
    val område: String,
    val listetype: Boolean,
    val påkrevd: Boolean,
    val verdi: String,
    val verdiBigInt: Long?
)