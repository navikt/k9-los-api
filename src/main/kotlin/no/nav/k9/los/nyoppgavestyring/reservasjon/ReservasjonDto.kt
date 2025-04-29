package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDateTime

data class ReservasjonDto(
    val reservertAvIdent: String,
    val reservertAvEpost: String,
    val reservertAvNavn: String?,
    val saksnummer: String?,
    val journalpostId: String?,
    val behandlingType: BehandlingType,
    val reservertTilTidspunkt: LocalDateTime,
    val kommentar: String,
    val tilBeslutter: Boolean,
    val oppgavenøkkel: OppgaveNøkkelDto,
)