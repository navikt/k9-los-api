package no.nav.k9.los.tjenester.saksbehandler

class IdTokenInvalidFormatException(idToken: IdToken, cause: Throwable? = null) :
    RuntimeException("$idToken er på ugyldig format.", cause)