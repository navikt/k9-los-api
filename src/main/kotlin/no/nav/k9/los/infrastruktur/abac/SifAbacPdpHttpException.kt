package no.nav.k9.los.infrastruktur.abac

/**
 * Kastes ved ikke-2xx fra sif-abac-pdp. Arver IllegalStateException for å være kompatibel med
 * feilhåndteringen de øvrige kallene i [SifAbacPdpKlient] bruker, men eksponerer statuskoden typet
 * slik at kallere kan logge den uten å måtte parse feilmeldingen.
 */
internal class SifAbacPdpHttpException(
    val status: Int,
    operasjon: String,
) : IllegalStateException("Feil ved '$operasjon' mot sif-abac-pdp: HTTP $status")
