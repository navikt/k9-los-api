package no.nav.k9.los.søkeboks

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
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
        val oppgavesøkere = Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk())
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
        val tjeneste = SøkeboksTjeneste(pdl, pep, builder, query, Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk()))

        assertThat(tjeneste.finnOppgaverSammendrag(Områder.K9, IdTokenLocal(),"SAK-1")).isEqualTo(SøkeresultatSammendrag.IkkeTilgang)
        coVerify { pdl wasNot Called }
        coVerify { builder wasNot Called }
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
