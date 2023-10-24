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
    val oppgaveEksternId: String,
    val journalpostId: String?,
    //val opprettetTidspunkt: LocalDateTime, //TODO enten forsvare fjerning, eller hente fra første oppgaveversjon
    val oppgavestatus: Oppgavestatus,
    val oppgavebehandlingsUrl: String,
) {
    constructor(oppgave: Oppgave, person: PersonPdl) : this(
        søkersNavn = person.navn(),
        søkersPersonnr = person.fnr(),
        behandlingstype = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!),
        saksnummer = oppgave.hentVerdi("saksnummer")!!,
        oppgaveEksternId = oppgave.eksternId,
        journalpostId = "",
        oppgavestatus = Oppgavestatus.fraKode(oppgave.status),
        oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl()
    )
}