package no.nav.k9.los.søkeboks.ung

import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.doedsdato
import no.nav.k9.los.infrastruktur.pdl.fnr
import no.nav.k9.los.infrastruktur.pdl.kjoenn
import no.nav.k9.los.infrastruktur.pdl.navn
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.typesikker.Felt
import no.nav.k9.los.oppgavedefinisjon.typesikker.hent
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

class UngOppgavesøk : Oppgavesøk {
    override fun lagQuery(søkeord: Søkeord): OppgaveQuery? = when (søkeord) {
        is Søkeord.Person -> søkeord.aktørIder.takeIf { it.isNotEmpty() }
            ?.let { query(UngdomsprogramytelsenFelter.Sak.AKTOR_ID, EksternFeltverdiOperator.IN, it) }
        is Søkeord.Journalpost -> null
        is Søkeord.Sak -> query(
            UngdomsprogramytelsenFelter.Sak.SAKSNUMMER,
            EksternFeltverdiOperator.EQUALS,
            listOf(søkeord.saksnummer),
        )
    }

    override fun aktørId(oppgave: Oppgave) = oppgave.hent(UngdomsprogramytelsenFelter.Sak.AKTOR_ID)

    override fun saksnummer(oppgave: Oppgave) = oppgave.hent(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER)

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
        behandlingstype = oppgave.hent(UngdomsprogramytelsenFelter.Behandling.TYPEKODE)?.tilDto(),
        saksnummer = oppgave.hent(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER),
        journalpostId = null,
        fagsakÅr = null,
        opprettetTidspunkt = oppgave.hent(UngdomsprogramytelsenFelter.Sak.REGISTRERT_DATO),
        oppgavestatus = KodeOgNavnDto(oppgave.status.kode, oppgave.status.visningsnavn),
        behandlingsstatus = oppgave.hent(UngdomsprogramytelsenFelter.Behandling.STATUS)?.tilDto(),
        oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
        hastesak = false,
    )

    private fun query(felt: Felt<*>, operator: EksternFeltverdiOperator, verdi: List<String>) = OppgaveQuery(
        filtere = listOf(FeltverdiOppgavefilter(Områder.UNGDOMSPROGRAMYTELSEN, felt.id, operator, verdi)),
        order = listOf(
            EnkelOrderFelt(
                Områder.UNGDOMSPROGRAMYTELSEN,
                UngdomsprogramytelsenFelter.Sak.MOTTATT_DATO.id,
                false,
            ),
        ),
    )

    private fun Kodeverdi.tilDto() = KodeOgNavnDto(kode, navn)
}
