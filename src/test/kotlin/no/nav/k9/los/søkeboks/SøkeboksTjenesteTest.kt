package no.nav.k9.los.søkeboks

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.PersonPdlResponse
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.søkeboks.k9.K9Oppgavesøk
import no.nav.k9.los.søkeboks.aktivitetspenger.AktivitetspengerOppgavesøk
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

class SøkeboksTjenesteTest {
    @Test
    fun `nytt søk beholder gruppering pep og obsolete-filter og gjenbruker person`() = runBlocking {
        val queryService = mockk<OppgaveQueryService>()
        val pdlService = mockk<IPdlService>()
        val pepClient = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val lukket = oppgave("lukket", "SAK-1", Oppgavestatus.LUKKET)
        val åpen = oppgave("åpen", "SAK-1", Oppgavestatus.AAPEN)
        val obsolete = oppgave("obsolete", "SAK-2", Oppgavestatus.AAPEN, "OBSOLETE")
        val person = person()
        val brukerkontekst = TestKontekstFactory.brukerkontekst(Områder.K9)
        every { queryService.queryForOppgave(any()) } returns listOf(lukket, åpen, obsolete)
        coEvery { pdlService.person("aktor-1", any()) } returns PersonPdlResponse(false, person)
        coEvery { pepClient.harTilgangTilOppgaveV3(any(), brukerkontekst, any()) } returns true
        coEvery { builder.bygg(listOf(åpen), brukerkontekst, mapOf("aktor-1" to person)) } returns emptyList()
        val oppgavesøkere = Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk())
        val tjeneste = SøkeboksTjeneste(pdlService, pepClient, builder, queryService, oppgavesøkere)

        val resultat = tjeneste.finnOppgaverSammendrag("123456789", Områder.K9, brukerkontekst)

        assertThat(resultat).isEqualTo(SøkeresultatSammendrag.MedResultat(emptyList()))
        coVerify(exactly = 1) { pdlService.person("aktor-1", any()) }
        coVerify(exactly = 1) { builder.bygg(listOf(åpen), brukerkontekst, mapOf("aktor-1" to person)) }
        verify(exactly = 1) {
            queryService.queryForOppgave(match { it.område == Områder.K9 && it.harTilgangTilKode6 == false })
        }
    }

    @Test
    fun `AP og feil kontekst avvises før PDL og query i begge søk`() = runBlocking {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val tjeneste = SøkeboksTjeneste(pdl, pep, builder, query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk()))
        for (område in Områder.entries) {
            val bruker = TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER)
            assertFailsWith<IllegalArgumentException> { tjeneste.finnOppgaver("12345678901", område, bruker) }
            assertFailsWith<IllegalArgumentException> { tjeneste.finnOppgaverSammendrag("12345678901", område, bruker) }
        }
        coVerify { pdl wasNot Called }
        coVerify { pep wasNot Called }
        coVerify { builder wasNot Called }
        verify { query wasNot Called }
    }

    @Test
    fun `PEP avslag gir ingen personoppslag eller DTO i begge søk`() = runBlocking {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val bruker = TestKontekstFactory.brukerkontekst(Områder.K9)
        every { query.queryForOppgave(any()) } returns listOf(oppgave("1", "SAK-1", Oppgavestatus.AAPEN))
        coEvery { pep.harTilgangTilOppgaveV3(any(), bruker, any()) } returns false
        val tjeneste = SøkeboksTjeneste(pdl, pep, builder, query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk()))

        assertThat(tjeneste.finnOppgaver("SAK-1", Områder.K9, bruker)).isEqualTo(Søkeresultat.IkkeTilgang)
        assertThat(tjeneste.finnOppgaverSammendrag("SAK-1", Områder.K9, bruker)).isEqualTo(SøkeresultatSammendrag.IkkeTilgang)
        coVerify { pdl wasNot Called }
        coVerify { builder wasNot Called }
    }

    @Test
    fun `kode6 tilgang sendes eksplisitt fra kontekst i begge søk`() = runBlocking {
        val query = mockk<OppgaveQueryService>()
        every { query.queryForOppgave(any()) } returns emptyList()
        val bruker = TestKontekstFactory.brukerkontekst(
            Områder.K9,
            tilganger = TestKontekstFactory.ALLE_TILGANGER.copy(harTilgangTilKode6 = true),
        )
        val tjeneste = SøkeboksTjeneste(mockk(), mockk(), mockk(), query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk()))
        tjeneste.finnOppgaver("SAK-1", Områder.K9, bruker)
        tjeneste.finnOppgaverSammendrag("SAK-1", Områder.K9, bruker)
        verify(exactly = 2) { query.queryForOppgave(match { it.harTilgangTilKode6 == true && it.område == Områder.K9 }) }
    }

    private fun oppgave(
        eksternId: String,
        saksnummer: String,
        status: Oppgavestatus,
        ytelse: String = "PSB",
    ): Oppgave {
        val oppgavetype = mockk<Oppgavetype>()
        every { oppgavetype.eksternId } returns "k9sak"
        every { oppgavetype.område } returns Område(1, "K9")
        every { oppgavetype.oppgavebehandlingsUrlTemplate } returns null
        return Oppgave(
            eksternId, "1", eksternId, oppgavetype, status, LocalDateTime.now(),
            listOf(
                Oppgavefelt("aktorId", Områder.K9, false, false, "aktor-1", null),
                Oppgavefelt("saksnummer", Områder.K9, false, false, saksnummer, null),
                Oppgavefelt("ytelsestype", Områder.K9, false, false, ytelse, null),
            ),
        )
    }

    private fun person() = PersonPdl(
        PersonPdl.Data(
            PersonPdl.Data.HentPerson(
                listOf(PersonPdl.Data.HentPerson.Folkeregisteridentifikator("12345678901")),
                listOf(PersonPdl.Data.HentPerson.Navn("Nordmann", "Ola Nordmann", "Ola", null)),
                emptyList(),
                emptyList(),
            )
        )
    )
}
