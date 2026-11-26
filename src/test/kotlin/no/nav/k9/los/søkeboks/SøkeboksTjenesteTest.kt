package no.nav.k9.los.søkeboks

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.pdl.*
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import no.nav.k9.los.søkeboks.aktivitetspenger.AktivitetspengerOppgavesøk
import no.nav.k9.los.søkeboks.k9.K9Oppgavesøk
import no.nav.k9.los.søkeboks.ung.UngOppgavesøk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

private const val FNR = "12345678901"

class SøkeboksTjenesteTest {
    @Test
    fun `nytt søk beholder gruppering pep og obsolete-filter og gjenbruker person`() = runTest {
        val queryService = mockk<OppgaveQueryService>()
        val pdlService = mockk<IPdlService>()
        val pepClient = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val lukket = oppgave("lukket", "SAK-1", Oppgavestatus.LUKKET)
        val åpen = oppgave("åpen", "SAK-1", Oppgavestatus.AAPEN)
        val obsolete = oppgave("obsolete", "SAK-2", Oppgavestatus.AAPEN, "OBSOLETE")
        val person = person()
        every { queryService.queryForOppgave(any()) } returns listOf(lukket, åpen, obsolete)
        coEvery { pdlService.person("aktor-1") } returns PersonPdlResponse(false, person)
        coEvery { pepClient.harTilgangTilOppgaveV3(any<Områder>(), any<IIdToken>(), any(), any()) } returns true
        coEvery { builder.bygg(listOf(åpen), mapOf("aktor-1" to person)) } returns emptyList()
        val oppgavesøkere = Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk())
        val tjeneste = SøkeboksTjeneste(pdlService, pepClient, builder, queryService, oppgavesøkere)

        val resultat = tjeneste.finnOppgaverSammendrag(Områder.K9, IdTokenLocal(), "123456789")

        assertThat(resultat).isEqualTo(SøkeresultatSammendrag.MedResultat(emptyList()))
        coVerify(exactly = 1) { pdlService.person("aktor-1") }
        coVerify(exactly = 1) { builder.bygg(listOf(åpen), mapOf("aktor-1" to person)) }
        verify(exactly = 1) {
            queryService.queryForOppgave(match { it.område == Områder.K9 })
        }
    }

    @Test
    fun `PEP avslag gir ingen personoppslag eller DTO i begge søk`() = runTest {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        every { query.queryForOppgave(any()) } returns listOf(oppgave("1", "SAK-1", Oppgavestatus.AAPEN))
        coEvery { pep.harTilgangTilOppgaveV3(any<Områder>(), any<IIdToken>(), any(), any()) } returns false
        val tjeneste = SøkeboksTjeneste(pdl, pep, builder, query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk()))

        assertThat(tjeneste.finnOppgaverSammendrag(Områder.K9, IdTokenLocal(),"SAK-1")).isEqualTo(SøkeresultatSammendrag.IkkeTilgang)
        coVerify { pdl wasNot Called }
        coVerify { builder wasNot Called }
    }

    @Test
    fun `aktivitetspenger - mangler tilgang til person gir IkkeTilgang uten oppgavesøk`() = runTest {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        coEvery { pdl.identifikator(FNR) } returns identifikatorRespons("aktor-1")
        coEvery { pep.harTilgangTilPersoner(Områder.AKTIVITETSPENGER, any(), listOf("aktor-1"), any()) } returns false
        val tjeneste = SøkeboksTjeneste(pdl, pep, builder, query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk()))

        val resultat = tjeneste.finnOppgaverSammendrag(Områder.AKTIVITETSPENGER, IdTokenLocal(), FNR)

        assertThat(resultat).isEqualTo(SøkeresultatSammendrag.IkkeTilgang)
        verify { query wasNot Called }
    }

    @Test
    fun `ukjent fnr gir TomtResultat uten tilgangssjekk`() = runTest {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        coEvery { pdl.identifikator(FNR) } returns PdlResponse(false, null)
        val tjeneste = SøkeboksTjeneste(pdl, pep, builder, query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk()))

        val resultat = tjeneste.finnOppgaverSammendrag(Områder.AKTIVITETSPENGER, IdTokenLocal(), FNR)

        assertThat(resultat).isEqualTo(SøkeresultatSammendrag.TomtResultat)
        coVerify { pep wasNot Called }
    }

    @Test
    fun `UNG saksøk bruker riktig område og gjenbruker personen i sammendraget`() = runTest {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val område = Områder.UNGDOMSPROGRAMYTELSEN
        val oppgave = oppgave("ung-1", "UNG-SAK-12", Oppgavestatus.AAPEN, område = område)
        val person = person()
        val token = IdTokenLocal()
        every { query.queryForOppgave(match { it.område == område }) } returns listOf(oppgave)
        coEvery { pep.harTilgangTilOppgaveV3(område, token, oppgave, any()) } returns true
        coEvery { pdl.person("aktor-1") } returns PersonPdlResponse(false, person)
        coEvery { builder.bygg(listOf(oppgave), mapOf("aktor-1" to person)) } returns emptyList()
        val tjeneste = SøkeboksTjeneste(
            pdl, pep, builder, query,
            Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk()),
        )

        assertThat(tjeneste.finnOppgaverSammendrag(område, token, "UNG-SAK-12"))
            .isEqualTo(SøkeresultatSammendrag.MedResultat(emptyList()))
        coVerify(exactly = 1) { pep.harTilgangTilOppgaveV3(område, token, oppgave, any()) }
        coVerify(exactly = 1) { pdl.person("aktor-1") }
        coVerify(exactly = 1) { builder.bygg(listOf(oppgave), mapOf("aktor-1" to person)) }
    }

    @Test
    fun `UNG person uten tilgang gir IkkeTilgang uten oppgavesøk`() = runTest {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        coEvery { pdl.identifikator(FNR) } returns identifikatorRespons("aktor-1")
        coEvery { pep.harTilgangTilPersoner(Områder.UNGDOMSPROGRAMYTELSEN, any(), listOf("aktor-1"), any()) } returns false
        val tjeneste = SøkeboksTjeneste(
            pdl, pep, builder, query,
            Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk()),
        )

        assertThat(tjeneste.finnOppgaverSammendrag(Områder.UNGDOMSPROGRAMYTELSEN, IdTokenLocal(), FNR))
            .isEqualTo(SøkeresultatSammendrag.IkkeTilgang)
        verify { query wasNot Called }
        coVerify { builder wasNot Called }
    }

    @Test
    fun `UNG journalpostsøk gir TomtResultat uten oppgavesøk`() = runTest {
        val pdl = mockk<IPdlService>()
        val query = mockk<OppgaveQueryService>()
        val pep = mockk<IPepClient>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val tjeneste = SøkeboksTjeneste(
            pdl, pep, builder, query,
            Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk(), UngOppgavesøk()),
        )

        assertThat(tjeneste.finnOppgaverSammendrag(Områder.UNGDOMSPROGRAMYTELSEN, IdTokenLocal(), "123456789"))
            .isEqualTo(SøkeresultatSammendrag.TomtResultat)
        verify { query wasNot Called }
        coVerify { pdl wasNot Called }
        coVerify { pep wasNot Called }
        coVerify { builder wasNot Called }
    }

    private fun identifikatorRespons(aktørId: String) = PdlResponse(
        false,
        AktøridPdl(
            data = AktøridPdl.Data(
                hentIdenter = AktøridPdl.Data.HentIdenter(
                    identer = listOf(AktøridPdl.Data.HentIdenter.Identer(ident = aktørId, historisk = false, gruppe = "AKTORID"))
                )
            )
        )
    )

    private fun oppgave(
        eksternId: String,
        saksnummer: String,
        status: Oppgavestatus,
        ytelse: String = "PSB",
        område: Områder = Områder.K9,
    ): Oppgave {
        val oppgavetype = mockk<Oppgavetype>()
        every { oppgavetype.eksternId } returns "k9sak"
        every { oppgavetype.område } returns Område(1, område.eksternId)
        every { oppgavetype.oppgavebehandlingsUrlTemplate } returns null
        return Oppgave(
            eksternId, "1", eksternId, oppgavetype, status, LocalDateTime.now(),
            listOf(
                Oppgavefelt("aktorId", område, false, false, "aktor-1", null),
                Oppgavefelt("saksnummer", område, false, false, saksnummer, null),
                Oppgavefelt("ytelsestype", område, false, false, ytelse, null),
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
