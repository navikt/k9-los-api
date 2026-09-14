package no.nav.k9.los.infrastruktur.utils

class IkkeImplementertException(override val message: String = "Handlingen er ikke implementert") :
    RuntimeException(message)