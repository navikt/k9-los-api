package no.nav.k9.los.søkeboks.ung

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenFelter
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.oppgaveuthenting.sammendrag.KodeOgNavnDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.PersonSammendragDto
import no.nav.k9.los.søkeboks.Oppgavesøkere
import no.nav.k9.los.søkeboks.Søkeord
import no.nav.k9.los.søkeboks.aktivitetspenger.AktivitetspengerOppgavesøk
import no.nav.k9.los.søkeboks.k9.K9Oppgavesøk
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class UngOppgavesøkTest {
    private val søk = UngOppgavesøk()

    @Test
    fun `person-query filtrerer aktør-ider med IN og sorterer på mottattdato`() {
        val aktørIder = listOf("aktor-1", "aktor-2")

        assertThat(søk.lagQuery(Søkeord.Person("fnr", false, aktørIder))).isEqualTo(
            query(UngdomsprogramytelsenFelter.Sak.AKTOR_ID.id, EksternFeltverdiOperator.IN, aktørIder),
        )
    }

    @Test
    fun `person-query med tom aktør-id-liste gir ingen query`() {
        assertThat(søk.lagQuery(Søkeord.Person("fnr", false, emptyList()))).isNull()
    }

    @Test
    fun `journalpost-query støttes ikke`() {
        assertThat(søk.lagQuery(Søkeord.Journalpost("journalpost-1"))).isNull()
    }

    @Test
    fun `sak-query bruker eksakt saksnummer uten normalisering`() {
        val saksnummer = "ung-123oI"

        assertThat(søk.lagQuery(Søkeord.Sak(saksnummer))).isEqualTo(
            query(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER.id, EksternFeltverdiOperator.EQUALS, listOf(saksnummer)),
        )
    }

    @Test
    fun `dispatcher velger UNG-søket`() {
        val dispatcher = Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), søk)

        assertThat(dispatcher.forOmråde(Områder.UNGDOMSPROGRAMYTELSEN)).isEqualTo(søk)
        assertThat(dispatcher.forOmråde(Områder.UNGDOMSPROGRAMYTELSEN).lagQuery(Søkeord.Sak("ung-123"))).isEqualTo(
            query(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER.id, EksternFeltverdiOperator.EQUALS, listOf("ung-123")),
        )
    }

    @Test
    fun `typed felter hentes fra oppgaven og UNG er synlig`() {
        val oppgave = oppgave(
            felter = listOf(
                felt(UngdomsprogramytelsenFelter.Sak.AKTOR_ID.id, AKTØR_ID),
                felt(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER.id, "UNG-SAK-1"),
            ),
        )

        assertThat(søk.aktørId(oppgave)).isEqualTo(AKTØR_ID)
        assertThat(søk.saksnummer(oppgave)).isEqualTo("UNG-SAK-1")
        assertThat(søk.erSynlig(oppgave)).isTrue()
    }

    @Test
    fun `sammendrag inkluderer person kodeverk registrertdato url og standardverdier`() {
        val registrert = LocalDateTime.parse("2026-03-04T05:06:07")
        val oppgave = oppgave(
            felter = listOf(
                felt(UngdomsprogramytelsenFelter.Sak.AKTOR_ID.id, AKTØR_ID),
                felt(UngdomsprogramytelsenFelter.Sak.SAKSNUMMER.id, "UNG-SAK-1"),
                felt(UngdomsprogramytelsenFelter.Sak.REGISTRERT_DATO.id, registrert.toString()),
                felt(UngdomsprogramytelsenFelter.Sak.MOTTATT_DATO.id, "2026-01-02T03:04:05"),
                felt(UngdomsprogramytelsenFelter.Behandling.TYPEKODE.id, BehandlingType.FØRSTEGANGSSØKNAD.kode),
                felt(UngdomsprogramytelsenFelter.Behandling.STATUS.id, BehandlingStatus.UTREDES.kode),
            ),
        )

        assertThat(søk.tilSammendrag(oppgave, person())).isEqualTo(
            OppgaveSammendragDto(
                oppgaveNøkkel = OppgaveNøkkelDto(oppgave),
                reservasjonsnøkkel = "reservasjonsnokkel",
                person = PersonSammendragDto(
                    navn = "Ola Nordmann",
                    fnr = "12345678901",
                    kjønn = "KVINNE",
                    dødsdato = LocalDate.parse("2024-02-03"),
                ),
                ytelse = null,
                behandlingstype = KodeOgNavnDto(BehandlingType.FØRSTEGANGSSØKNAD.kode, BehandlingType.FØRSTEGANGSSØKNAD.navn),
                saksnummer = "UNG-SAK-1",
                journalpostId = null,
                fagsakÅr = null,
                opprettetTidspunkt = registrert,
                oppgavestatus = KodeOgNavnDto(Oppgavestatus.VENTER.kode, Oppgavestatus.VENTER.visningsnavn),
                behandlingsstatus = KodeOgNavnDto(BehandlingStatus.UTREDES.kode, BehandlingStatus.UTREDES.navn),
                oppgavebehandlingsUrl = "https://ung.example/UNG-SAK-1/$AKTØR_ID",
                hastesak = false,
            ),
        )
    }

    @Test
    fun `sammendrag uten person beholder null-person og valgfrie defaults`() {
        val oppgave = oppgave(felter = emptyList(), urlTemplate = null)

        assertThat(søk.tilSammendrag(oppgave, null)).isEqualTo(
            OppgaveSammendragDto(
                oppgaveNøkkel = OppgaveNøkkelDto(oppgave),
                reservasjonsnøkkel = "reservasjonsnokkel",
                person = null,
                ytelse = null,
                behandlingstype = null,
                saksnummer = null,
                journalpostId = null,
                fagsakÅr = null,
                opprettetTidspunkt = null,
                oppgavestatus = KodeOgNavnDto(Oppgavestatus.VENTER.kode, Oppgavestatus.VENTER.visningsnavn),
                behandlingsstatus = null,
                oppgavebehandlingsUrl = null,
                hastesak = false,
            ),
        )
    }

    private fun query(feltId: String, operator: EksternFeltverdiOperator, verdi: List<String>) = OppgaveQuery(
        filtere = listOf(FeltverdiOppgavefilter(Områder.UNGDOMSPROGRAMYTELSEN, feltId, operator, verdi)),
        order = listOf(
            EnkelOrderFelt(
                Områder.UNGDOMSPROGRAMYTELSEN,
                UngdomsprogramytelsenFelter.Sak.MOTTATT_DATO.id,
                false,
            ),
        ),
    )

    private fun oppgave(
        felter: List<Oppgavefelt>,
        urlTemplate: String? = "https://ung.example/{saksnummer}/{aktorId}",
    ): Oppgave {
        val oppgavetype = Oppgavetype(
            eksternId = "ungSak",
            område = Område(1, Områder.UNGDOMSPROGRAMYTELSEN.eksternId),
            oppgavebehandlingsUrlTemplate = urlTemplate,
            oppgavefelter = emptySet(),
        )
        return Oppgave(
            eksternId = "ung-oppgave-1",
            eksternVersjon = "1",
            reservasjonsnøkkel = "reservasjonsnokkel",
            oppgavetype = oppgavetype,
            status = Oppgavestatus.VENTER,
            endretTidspunkt = LocalDateTime.parse("2026-03-04T05:06:07"),
            felter = felter,
        )
    }

    private fun felt(id: String, verdi: String) =
        Oppgavefelt(id, Områder.UNGDOMSPROGRAMYTELSEN, false, false, verdi, null)

    private fun person() = PersonPdl(
        PersonPdl.Data(
            PersonPdl.Data.HentPerson(
                folkeregisteridentifikator = listOf(PersonPdl.Data.HentPerson.Folkeregisteridentifikator("12345678901")),
                navn = listOf(PersonPdl.Data.HentPerson.Navn("Nordmann", null, "Ola", null)),
                kjoenn = listOf(PersonPdl.Data.HentPerson.Kjoenn("KVINNE")),
                doedsfall = listOf(PersonPdl.Data.HentPerson.Doedsfall(LocalDate.parse("2024-02-03"))),
            ),
        ),
    )

    private companion object {
        const val AKTØR_ID = "ung-aktor-1"
    }
}
