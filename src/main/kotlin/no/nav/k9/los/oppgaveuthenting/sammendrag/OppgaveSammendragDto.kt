package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import java.time.LocalDateTime

data class OppgaveSammendragDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val reservasjonsnøkkel: String,
    val person: PersonSammendragDto?,
    val ytelse: KodeOgNavnDto?,
    val behandlingstype: KodeOgNavnDto?,
    val saksnummer: String?,
    val journalpostId: String?,
    val fagsakÅr: Int?,
    val opprettetTidspunkt: LocalDateTime?,
    val oppgavestatus: KodeOgNavnDto,
    val behandlingsstatus: KodeOgNavnDto?,
    val oppgavebehandlingsUrl: String?,
    val hastesak: Boolean,
)
