package no.nav.k9.los.nyoppgavestyring.søkeboks

data class SøkQuery (
    val searchString: String,
    val fraAktiv: Boolean = true,
)
