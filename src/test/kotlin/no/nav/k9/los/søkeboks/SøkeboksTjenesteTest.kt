package no.nav.k9.los.søkeboks

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.abac.IPepClient
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
import no.nav.k9.los.søkeboks.ung.UngOppgavesøk
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

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
        every { queryService.queryForOppgave(any()) } returns listOf(lukket, åpen, obsolete)
        coEvery { pdlService.person("aktor-1") } returns PersonPdlResponse(false, person)
        coEvery { pepClient.harTilgangTilOppgaveV3(any()) } returns true
        coEvery { builder.bygg(listOf(åpen), mapOf("aktor-1" to person)) } returns emptyList()
        val oppgavesøkere = Oppgavesøkere(K9Oppgavesøk(), UngOppgavesøk())
        val tjeneste = SøkeboksTjeneste(pdlService, pepClient, builder, queryService, oppgavesøkere)

        val resultat = tjeneste.finnOppgaverSammendrag("123456789", Områder.K9)

        assertThat(resultat).isEqualTo(SøkeresultatSammendrag.MedResultat(emptyList()))
        coVerify(exactly = 1) { pdlService.person("aktor-1") }
        coVerify(exactly = 1) { builder.bygg(listOf(åpen), mapOf("aktor-1" to person)) }
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
