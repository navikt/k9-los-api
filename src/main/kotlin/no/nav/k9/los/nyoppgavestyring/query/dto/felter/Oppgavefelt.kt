package no.nav.k9.los.nyoppgavestyring.query.dto.felter

class Oppgavefelt(
    val område: String?,
    val kode: String,
    val visningsnavn: String,
    val tolkes_som: String,
    val verdier: List<Verdiforklaring>
)