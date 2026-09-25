package no.nav.k9.los.reservasjon

import java.time.LocalDate

data class ReservasjonEndringDto (
    val reservasjonsnøkkel: String,
    val brukerIdent: String? = null,
    val reserverTil: LocalDate? = null,
    val begrunnelse: String? = null
)
