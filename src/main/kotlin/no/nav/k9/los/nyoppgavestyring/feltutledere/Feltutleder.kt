package no.nav.k9.los.nyoppgavestyring.feltutledere

interface Feltutleder {
    val påkrevdeFelter: HashMap<String, String> // Verdi må her være en klasse med statiske felter
    fun utled()
}
