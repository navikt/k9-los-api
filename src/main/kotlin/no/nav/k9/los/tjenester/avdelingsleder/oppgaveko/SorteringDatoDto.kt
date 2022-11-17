package no.nav.k9.los.tjenester.avdelingsleder.oppgaveko

import java.time.LocalDate

data class SorteringDatoDto (
    val id: String,
    var fomDato: LocalDate?,
    var tomDato: LocalDate?
)
