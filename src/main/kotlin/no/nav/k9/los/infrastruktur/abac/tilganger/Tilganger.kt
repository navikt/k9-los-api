package no.nav.k9.los.infrastruktur.abac.tilganger

data class Tilganger(
    val basis: Boolean,
    val kode6: Boolean,
    val oppgavestyring: Boolean,
    val reservering: Boolean,
    val drift: Boolean,
)
