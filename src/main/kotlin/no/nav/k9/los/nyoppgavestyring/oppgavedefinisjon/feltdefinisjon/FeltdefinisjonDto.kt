package no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.feltdefinisjon

data class FeltdefinisjonDto(
    val id: String,
    val visningsnavn: String,
    val beskrivelse: String?,
    val listetype: Boolean,
    val tolkesSom: String,
    val synlighet: Synlighet,
    val kodeverkreferanse: KodeverkReferanseDto?,
    val transientFeltutleder: String?
)
