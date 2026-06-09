package no.nav.k9.los.feilhandtering

class MissingDefaultException(message: String, cause: Throwable? = null): IllegalArgumentException(message, cause)