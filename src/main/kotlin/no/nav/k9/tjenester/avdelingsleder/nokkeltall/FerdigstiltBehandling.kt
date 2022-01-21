package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import java.time.LocalDate

data class FerdigstiltBehandling(
    val behandlendeEnhet: String,
    val dato: LocalDate
)