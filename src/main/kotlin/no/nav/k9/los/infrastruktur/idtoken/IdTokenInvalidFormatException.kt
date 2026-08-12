package no.nav.k9.los.infrastruktur.idtoken

/**
 * Merk: tokenet selv legges bevisst ikke i meldingen, siden exception-meldinger havner i logg.
 */
class IdTokenInvalidFormatException(cause: Throwable? = null) :
    RuntimeException("Id-token er på ugyldig format.", cause)
