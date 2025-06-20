package no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl


data class Error(
    val errors: List<Error>
) {
    data class Error(
        val extensions: Extensions?,
        val locations: List<Location>?,
        val message: String?,
        val path: List<String>?
    ) {
        data class Extensions(
            val classification: String?,
            val code: String?
        )

        data class Location(
            val column: Int?,
            val line: Int?
        )
    }
}