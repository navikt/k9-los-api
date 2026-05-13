package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
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
    val hastesak: Boolean,
) {
    constructor(oppgaveV3: Oppgave, person: PersonPdl?) : this(
        søkersNavn = person?.navn() ?: "Ukjent navn",
        søkersPersonnr = person?.fnr() ?: "Ukjent fnummer",
        søkersKjønn = person?.kjoenn() ?: "Ukjent kjønn",
        søkersDødsdato = person?.doedsdato(),
        behandlingstype = BehandlingType.fraKode(oppgaveV3.hentVerdi(K9FeltIder.BEHANDLING_TYPEKODE)!!),
        saksnummer = oppgaveV3.hentVerdi(K9FeltIder.SAKSNUMMER),
        oppgaveNøkkel = OppgaveNøkkelDto(oppgaveV3),
        journalpostId = oppgaveV3.hentVerdi(K9FeltIder.JOURNALPOST_ID),
        opprettetTidspunkt = oppgaveV3.hentVerdi(K9FeltIder.REGISTRERT_DATO)?.let { LocalDateTime.parse(it) },
        oppgavestatus = Oppgavestatus.fraKode(oppgaveV3.status),
        oppgavebehandlingsUrl = oppgaveV3.getOppgaveBehandlingsurl(),
        reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
        hastesak = oppgaveV3.hentVerdi(K9FeltIder.HASTESAK) == "true",
    )
}