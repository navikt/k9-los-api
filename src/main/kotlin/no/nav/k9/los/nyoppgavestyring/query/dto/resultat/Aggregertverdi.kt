package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon

data class Aggregertverdi(
    val type: Aggregeringsfunksjon,
    val område: String?,
    val kode: String?,
    val verdi: String?,
)
