package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import java.time.LocalDateTime
data class GenerellOppgaveV3Dto(
    val søkersNavn: String,
    val søkersPersonnr: String,
    val saksnummer: String,
    val oppgaveEksternId: String,
    val journalpostId: String?,
    //val opprettetTidspunkt: LocalDateTime, //TODO enten forsvare fjerning, eller hente fra første oppgaveversjon
    val oppgavestatus: Oppgavestatus,
    val oppgavebehandlingsUrl: String,
)