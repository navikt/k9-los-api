package no.nav.k9.tjenester.kokriterier

data class KøkriterierDto(
    val område: String,
    val system: String,
    val oppgaver: List<Oppgave>
)

data class Oppgave(
    val kode: String,
    val navn: String
)
