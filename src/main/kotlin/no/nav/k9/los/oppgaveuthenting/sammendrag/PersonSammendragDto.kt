package no.nav.k9.los.oppgaveuthenting.sammendrag

import java.time.LocalDate

data class PersonSammendragDto(
    val navn: String,
    val fnr: String,
    val kjønn: String,
    val dødsdato: LocalDate?,
)