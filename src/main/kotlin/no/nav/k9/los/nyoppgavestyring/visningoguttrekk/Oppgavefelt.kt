package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

class Oppgavefelt(
    val eksternId: String,
    val område: String,
    val listetype: Boolean,
    val påkrevd: Boolean,
    val verdi: String,
    val verdiBigInt: Long?
)