package no.nav.k9.tjenester.avdelingsleder.reservasjoner

import no.nav.k9.domene.modell.BehandlingType
import java.time.LocalDateTime
import java.util.*

data class ReservasjonDto(
        val reservertAvUid: String,
        val reservertAvNavn: String,
        val reservertTilTidspunkt: LocalDateTime,
        val oppgaveId: UUID,
        val saksnummer: String,
        val behandlingType: BehandlingType,
        val tilBeslutter: Boolean
)