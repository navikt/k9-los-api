package no.nav.k9.los.reservasjon

import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import java.time.LocalDateTime

data class ReservasjonDto(
    val reservertAvIdent: String,
    val reservertAvId: Long,
    val reservertAvEpost: String,
    val reservertAvNavn: String?,
    val saksnummer: String?,
    val journalpostId: String?,
    val ytelse: String,
    val behandlingType: BehandlingType,
    val reservertTilTidspunkt: LocalDateTime,
    val kommentar: String,
    val tilBeslutter: Boolean,
    val oppgavenøkkel: OppgaveNøkkelDto,
    val reservasjonsnøkkel: String,
)