package no.nav.k9.los.søkeboks.aktivitetspenger

import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFeltIder
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.doedsdato
import no.nav.k9.los.infrastruktur.pdl.fnr
import no.nav.k9.los.infrastruktur.pdl.kjoenn
import no.nav.k9.los.infrastruktur.pdl.navn
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.oppgaveuthenting.sammendrag.KodeOgNavnDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.PersonSammendragDto
import no.nav.k9.los.søkeboks.Oppgavesøk
import no.nav.k9.los.søkeboks.Søkeord
import no.nav.ung.kodeverk.api.Kodeverdi
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import java.time.LocalDateTime

class AktivitetspengerOppgavesøk : Oppgavesøk {
    override fun lagQuery(søkeord: Søkeord): OppgaveQuery? = when (søkeord) {
        is Søkeord.Person -> søkeord.aktørIder.takeIf { it.isNotEmpty() }
            ?.let { query(AktivitetspengerFeltIder.Sak.AKTOR_ID, EksternFeltverdiOperator.IN, it) }
        // Aktivitetspenger har ikke journalpostId som felt.
        is Søkeord.Journalpost -> null
        is Søkeord.Sak -> query(AktivitetspengerFeltIder.Sak.SAKSNUMMER, EksternFeltverdiOperator.EQUALS, listOf(søkeord.saksnummer))
    }

    override fun aktørId(oppgave: Oppgave) = oppgave.hentVerdi(AktivitetspengerFeltIder.Sak.AKTOR_ID)

    override fun saksnummer(oppgave: Oppgave) = oppgave.hentVerdi(AktivitetspengerFeltIder.Sak.SAKSNUMMER)

    override fun erSynlig(oppgave: Oppgave) = true

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
        ytelse = null,
        // TODO: Finn ut om "Aktivitetspenger" skal vises i tabellen til søkeboksen. Antar det er overflødig i aktivitetspenger-området.
//        ytelse = oppgave.hentVerdi(AktivitetspengerFeltIder.Vedtak.YTELSESTYPE)
//            ?.let { FagsakYtelseType.fraKode(it).tilDto() },
        behandlingstype = oppgave.hentVerdi(AktivitetspengerFeltIder.Behandling.TYPEKODE)
            ?.let { BehandlingType.fraKode(it).tilDto() },
        saksnummer = oppgave.hentVerdi(AktivitetspengerFeltIder.Sak.SAKSNUMMER),
        journalpostId = null,
        fagsakÅr = null,
        opprettetTidspunkt = oppgave.hentVerdi(AktivitetspengerFeltIder.Sak.REGISTRERT_DATO)?.let(LocalDateTime::parse),
        oppgavestatus = KodeOgNavnDto(oppgave.status.kode, oppgave.status.visningsnavn),
        behandlingsstatus = oppgave.hentVerdi(AktivitetspengerFeltIder.Behandling.STATUS)
            ?.let { BehandlingStatus.fraKode(it).tilDto() },
        oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
        hastesak = false,
    )

    private fun query(feltkode: String, operator: EksternFeltverdiOperator, verdi: List<String>) = OppgaveQuery(
        filtere = listOf(FeltverdiOppgavefilter(Områder.AKTIVITETSPENGER, feltkode, operator, verdi)),
        order = listOf(EnkelOrderFelt(Områder.AKTIVITETSPENGER, AktivitetspengerFeltIder.Sak.MOTTATT_DATO, false)),
    )

    private fun Kodeverdi.tilDto() = KodeOgNavnDto(kode, navn)
}
