package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.*
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import java.time.LocalDate
import java.time.LocalDateTime

data class GenerellOppgaveV3Dto(
    val søkersNavn: String,
    val søkersPersonnr: String,
    val søkersKjønn: String,
    val søkersDødsdato: LocalDate?,
    val behandlingstype: BehandlingType,
    val saksnummer: String?,
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val journalpostId: String?,
    val opprettetTidspunkt: LocalDateTime?,
    val oppgavestatus: Oppgavestatus,
    val oppgavebehandlingsUrl: String?,
    val reservasjonsnøkkel: String,
) {
    constructor(oppgaveV3: Oppgave, person: PersonPdl?) : this(
        søkersNavn = person?.navn() ?: "Ukjent navn",
        søkersPersonnr = person?.fnr() ?: "Ukjent fnummer",
        søkersKjønn = person?.kjoenn() ?: "Ukjent kjønn",
        søkersDødsdato = person?.doedsdato(),
        behandlingstype = BehandlingType.fraKode(oppgaveV3.hentVerdi("behandlingTypekode")!!),
        saksnummer = oppgaveV3.hentVerdi("saksnummer"),
        oppgaveNøkkel = OppgaveNøkkelDto(oppgaveV3),
        journalpostId = oppgaveV3.hentVerdi("journalpostId"),
        opprettetTidspunkt = oppgaveV3.hentVerdi("registrertDato")?.let { LocalDateTime.parse(it) },
        oppgavestatus = Oppgavestatus.fraKode(oppgaveV3.status),
        oppgavebehandlingsUrl = oppgaveV3.getOppgaveBehandlingsurl(),
        reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
    )
}