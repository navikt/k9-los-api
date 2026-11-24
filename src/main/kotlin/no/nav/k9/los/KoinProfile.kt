package no.nav.k9.los

enum class KoinProfile {
    LOCAL,
    VERDIKJEDE,
    PREPROD,
    PROD;

    val kjørerUtenforNais: Boolean
        get() = this == LOCAL || this == VERDIKJEDE
}
