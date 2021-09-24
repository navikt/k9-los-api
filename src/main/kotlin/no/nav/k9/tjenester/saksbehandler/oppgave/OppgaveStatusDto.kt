package no.nav.k9.tjenester.saksbehandler.oppgave

import java.time.LocalDateTime

class OppgaveStatusDto(
        val erReservert: Boolean,
        val reservertTilTidspunkt: LocalDateTime?,
        val erReservertAvInnloggetBruker: Boolean,
        val reservertAv: String?,
        val reservertAvNavn: String?,
        val flyttetReservasjon: FlyttetReservasjonDto?
)
