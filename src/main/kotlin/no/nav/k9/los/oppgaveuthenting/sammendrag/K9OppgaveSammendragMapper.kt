package no.nav.k9.los.oppgaveuthenting.sammendrag

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.doedsdato
import no.nav.k9.los.infrastruktur.pdl.fnr
import no.nav.k9.los.infrastruktur.pdl.kjoenn
import no.nav.k9.los.infrastruktur.pdl.navn
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.kodeverk.FagsakYtelseType
import no.nav.k9.los.kodeverk.Kodeverdi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import java.time.LocalDateTime

class K9OppgaveSammendragMapper : OppgaveSammendragMapper {
    override val område = Områder.K9

    override fun map(oppgave: Oppgave, person: PersonPdl?) = OppgaveSammendragDto(
        oppgaveNøkkel = OppgaveNøkkelDto(oppgave),
        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
        person = person?.let {
            PersonSammendragDto(
                navn = it.navn(),
                fnr = it.fnr(),
                kjønn = it.kjoenn(),
                dødsdato = it.doedsdato(),
            )
        },
        ytelse = oppgave.hentVerdi("ytelsestype")?.let { FagsakYtelseType.fraKode(it).tilDto() },
        behandlingstype = oppgave.hentVerdi("behandlingTypekode")?.let { BehandlingType.fraKode(it).tilDto() },
        saksnummer = oppgave.hentVerdi("saksnummer"),
        journalpostId = oppgave.hentVerdi("journalpostId"),
        fagsakÅr = oppgave.hentVerdi("fagsakÅr")?.toIntOrNull(),
        opprettetTidspunkt = oppgave.hentVerdi("registrertDato")?.let(LocalDateTime::parse),
        oppgavestatus = KodeOgNavnDto(oppgave.status.kode, oppgave.status.visningsnavn),
        behandlingsstatus = oppgave.hentVerdi("behandlingsstatus")?.let { BehandlingStatus.fraKode(it).tilDto() },
        oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
        hastesak = oppgave.hentVerdi("hastesak") == "true",
    )

    private fun Kodeverdi.tilDto() = KodeOgNavnDto(kode, navn)
}
