package no.nav.k9.los.nyoppgavestyring.ko.dto

import java.time.LocalDateTime

class OppgaveKoListeelement(
    val id: Long,
    val tittel: String,
    val sistEndret: LocalDateTime?,
    val antallSaksbehandlere: Int
)