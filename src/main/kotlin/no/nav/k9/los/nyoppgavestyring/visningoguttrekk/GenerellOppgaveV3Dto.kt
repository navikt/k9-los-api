package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.integrasjon.pdl.PersonPdl
import no.nav.k9.los.integrasjon.pdl.fnr
import no.nav.k9.los.integrasjon.pdl.navn
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus

data class GenerellOppgaveV3Dto(
    val søkersNavn: String,
    val søkersPersonnr: String,
    val behandlingstype: BehandlingType,
    val saksnummer: String,
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val journalpostId: String?,
    //val opprettetTidspunkt: LocalDateTime, //TODO enten forsvare fjerning, eller hente fra første oppgaveversjon
    val oppgavestatus: Oppgavestatus,
    val oppgavebehandlingsUrl: String,
) {
    constructor(oppgaveV3: Oppgave, person: PersonPdl) : this(
        søkersNavn = person.navn(),
        søkersPersonnr = person.fnr(),
        behandlingstype = BehandlingType.fraKode(oppgaveV3.hentVerdi("behandlingTypekode")!!),
        saksnummer = oppgaveV3.hentVerdi("saksnummer")!!,
        oppgaveNøkkel = OppgaveNøkkelDto(oppgaveV3),
        journalpostId = "",
        oppgavestatus = Oppgavestatus.fraKode(oppgaveV3.status),
        oppgavebehandlingsUrl = oppgaveV3.getOppgaveBehandlingsurl()
    )
}