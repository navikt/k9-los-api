package no.nav.k9.los.søkeboks.k9

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
import no.nav.k9.los.søkeboks.Oppgavesøk
import no.nav.k9.los.søkeboks.Søkeord
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.oppgaveuthenting.sammendrag.KodeOgNavnDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.PersonSammendragDto
import java.time.LocalDateTime

/**
 * Feltvokabularet til område K9. Se [Oppgavesøk] for hvorfor feltkodene er samlet her
 * og ikke deles med andre områder.
 */
class K9Oppgavesøk : Oppgavesøk {
    override fun lagQuery(søkeord: Søkeord): OppgaveQuery = when (søkeord) {
        is Søkeord.Person -> query("aktorId", EksternFeltverdiOperator.IN, søkeord.aktørIder + søkeord.fnr)
        is Søkeord.Journalpost -> query("journalpostId", EksternFeltverdiOperator.EQUALS, listOf(søkeord.journalpostId))
        is Søkeord.Sak -> query("saksnummer", EksternFeltverdiOperator.EQUALS, listOf(normaliserSaksnummer(søkeord.saksnummer)))
    }

    override fun aktørId(oppgave: Oppgave) = oppgave.hentVerdi("aktorId")

    override fun saksnummer(oppgave: Oppgave) = oppgave.hentVerdi("saksnummer")

    override fun erSynlig(oppgave: Oppgave) = oppgave.hentVerdi("ytelsestype") != "OBSOLETE"

    override fun tilSammendrag(oppgave: Oppgave, person: PersonPdl?) = OppgaveSammendragDto(
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

    private fun query(feltkode: String, operator: EksternFeltverdiOperator, verdi: List<String>) = OppgaveQuery(
        filtere = listOf(FeltverdiOppgavefilter(Områder.K9, feltkode, operator, verdi)),
        order = listOf(EnkelOrderFelt(Områder.K9, "mottattDato", false)),
    )

    /** Saksnummer skrives med stor forbokstav, men O og I er små for å skille dem fra 0 og 1. */
    private fun normaliserSaksnummer(saksnummer: String) =
        saksnummer.uppercase().replace("O", "o").replace("I", "i")

    private fun Kodeverdi.tilDto() = KodeOgNavnDto(kode, navn)
}
