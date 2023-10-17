package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import java.time.LocalDateTime

data class GenerellOppgaveV3Dto(
    val søkersNavn: String,
    val søkersPersonnr: String,
    val saksnummer: String,
    val oppgaveEksternId: String,
    val journalpostId: String,
    val opprettetTidspuntk: LocalDateTime,
    val oppgavestatus: Oppgavestatus,
)