package no.nav.k9.los.infrastruktur.abac

internal class SifAbacPdpHttpException(
    val status: Int,
    operasjon: String,
) : IllegalStateException("Feil ved '$operasjon' mot sif-abac-pdp: HTTP $status")
