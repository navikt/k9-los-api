package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon

data class Aggregertverdi(
    val type: Aggregeringsfunksjon,
    val område: String?,
    val kode: String?,
    /**
     * Typekontrakten for [verdi] bestemmes av [type] og feltets datatype:
     * - ANTALL → [Long]
     * - SUM (INTEGER) → [Long]
     * - GJENNOMSNITT (INTEGER) → [Double]
     * - MIN/MAKS (INTEGER) → [Long]
     * - MIN/MAKS (andre datatyper) → [String]
     * - null dersom det ikke finnes data å aggregere
     */
    val verdi: Any?,
)
