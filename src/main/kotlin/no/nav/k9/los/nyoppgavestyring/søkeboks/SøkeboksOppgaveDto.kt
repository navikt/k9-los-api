package no.nav.k9.los.nyoppgavestyring.søkeboks

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.*
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDate
import java.time.LocalDateTime

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class OppgavestatusMedNavn(val kode: String, val navn: String) {
    AAPEN("AAPEN", "Åpen"),
    VENTER("VENTER", "Venter"),
    LUKKET("LUKKET", "Lukket"),
}

data class SøkeboksOppgaveDto(
    val navn: String,
    val fnr: String,
    val kjønn: String,
    val dødsdato: LocalDate?,
    val ytelsestype: FagsakYtelseType,
    val behandlingstype: BehandlingType,
    val saksnummer: String?,
    val hastesak: Boolean,
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val journalpostId: String?,
    val opprettetTidspunkt: LocalDateTime?,
    val oppgavestatus: OppgavestatusMedNavn,
    val behandlingsstatus: BehandlingStatus?,
    val oppgavebehandlingsUrl: String?,
    val reservasjonsnøkkel: String,
    val reservertAvSaksbehandlerNavn: String?,
    val reservertAvSaksbehandlerIdent: String?,
    val reservertTom: LocalDateTime?,
) {
    constructor(oppgaveV3: Oppgave, person: PersonPdl?, reservasjon: ReservasjonV3?, reservertAv: Saksbehandler?) : this(
        navn = person?.navn() ?: "Ukjent navn",
        fnr = person?.fnr() ?: "Ukjent fnummer",
        kjønn = person?.kjoenn() ?: "Ukjent kjønn",
        dødsdato = person?.doedsdato(),
        ytelsestype = oppgaveV3.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it) } ?: FagsakYtelseType.UKJENT,
        behandlingstype = BehandlingType.fraKode(oppgaveV3.hentVerdi("behandlingTypekode")!!),
        saksnummer = oppgaveV3.hentVerdi("saksnummer"),
        hastesak = oppgaveV3.hentVerdi("hastesak") == "true",
        oppgaveNøkkel = OppgaveNøkkelDto(oppgaveV3),
        journalpostId = oppgaveV3.hentVerdi("journalpostId"),
        opprettetTidspunkt = oppgaveV3.hentVerdi("registrertDato")?.let { LocalDateTime.parse(it) },
        oppgavestatus = OppgavestatusMedNavn.valueOf(oppgaveV3.status),
        behandlingsstatus = oppgaveV3.hentVerdi("behandlingsstatus")?.let { BehandlingStatus.fraKode(it) },
        oppgavebehandlingsUrl = oppgaveV3.getOppgaveBehandlingsurl(),
        reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
        reservertAvSaksbehandlerNavn = reservertAv?.navn,
        reservertAvSaksbehandlerIdent = reservertAv?.brukerIdent,
        reservertTom = reservasjon?.gyldigTil,
    )
}
