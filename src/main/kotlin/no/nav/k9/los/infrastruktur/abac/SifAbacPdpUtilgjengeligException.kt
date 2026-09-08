package no.nav.k9.los.infrastruktur.abac

internal class SifAbacPdpUtilgjengeligException(cause: Throwable? = null) :
    IllegalStateException("Tidsavbrudd mot sif-abac-pdp", cause)
