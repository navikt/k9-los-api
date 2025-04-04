package no.nav.k9.los.nyoppgavestyring.infrastruktur.idtoken

class IdTokenInvalidFormatException(idToken: IdToken, cause: Throwable? = null) :
    RuntimeException("$idToken er på ugyldig format.", cause)