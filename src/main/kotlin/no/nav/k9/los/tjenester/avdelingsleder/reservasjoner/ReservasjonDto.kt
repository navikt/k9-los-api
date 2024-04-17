package no.nav.k9.los.tjenester.avdelingsleder.reservasjoner

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

data class ReservasjonDto(
    val reservertAvEpost: String,
    val saksnummer: String?,
    val journalpostId: String?,
    val behandlingType: BehandlingType,
    val reservertTilTidspunkt: LocalDateTime,
    val kommentar: String,
    val oppgavenøkkel: OppgaveNøkkelDto,
)