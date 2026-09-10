package no.nav.k9.los.sisteoppgaver

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.FeltType
import no.nav.k9.los.OppgaveTestDataBuilder
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.PersonPdlResponse
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get

class SisteOppgaverTjenesteTest : AbstractK9LosIntegrationTest() {
    private val kontekst = TestKontekstFactory.brukerkontekst(Områder.K9)
    private lateinit var pepClient: IPepClient
    private lateinit var pdlService: IPdlService
    private lateinit var tjeneste: SisteOppgaverTjeneste

    @BeforeEach
    fun setup() {
        pepClient = mockk()
        pdlService = mockk()
        tjeneste = SisteOppgaverTjeneste(get(), get(), pepClient, pdlService, get())
    }

    @Test
    fun `skal lagre og hente siste oppgaver`() = runTest {
        val oppgave = OppgaveTestDataBuilder().medOppgaveFeltVerdi(FeltType.AKTØR_ID, "1234567890123").lagOgLagre()
        coEvery { pepClient.harTilgangTilOppgaveV3(any(), kontekst, Action.read) } returns true
        coEvery { pdlService.person("1234567890123", kontekst) } returns PersonPdlResponse(false, mockk<PersonPdl>(relaxed = true))

        tjeneste.lagreSisteOppgave(OppgaveNøkkelDto(oppgave.eksternId, oppgave.oppgavetype.eksternId, Områder.K9), kontekst)
        val resultat = tjeneste.hentSisteOppgaver(kontekst)

        assertThat(resultat).hasSize(1)
        assertThat(resultat.single().oppgaveEksternId).isEqualTo(oppgave.eksternId)
        coVerify(exactly = 1) { pdlService.person("1234567890123", kontekst) }
    }

    @Test
    fun `skal filtrere bort nektet oppgave uten PDL kall`() = runTest {
        val tillatt = OppgaveTestDataBuilder().medOppgaveFeltVerdi(FeltType.AKTØR_ID, "1234567890123").lagOgLagre()
        val nektet = OppgaveTestDataBuilder().medOppgaveFeltVerdi(FeltType.AKTØR_ID, "9876543210987").lagOgLagre()
        coEvery { pepClient.harTilgangTilOppgaveV3(any(), kontekst, Action.read) } answers {
            firstArg<Oppgave>().eksternId == tillatt.eksternId
        }
        coEvery { pdlService.person("1234567890123", kontekst) } returns PersonPdlResponse(false, mockk<PersonPdl>(relaxed = true))
        listOf(tillatt, nektet).forEach {
            tjeneste.lagreSisteOppgave(OppgaveNøkkelDto(it.eksternId, it.oppgavetype.eksternId, Områder.K9), kontekst)
        }

        val resultat = tjeneste.hentSisteOppgaver(kontekst)

        assertThat(resultat.map { it.oppgaveEksternId }).isEqualTo(listOf(tillatt.eksternId))
        coVerify(exactly = 0) { pdlService.person("9876543210987", any()) }
    }

    @Test
    fun `skal avvise body fra annet område før lagring`() {
        val repository = mockk<SisteOppgaverRepository>()
        val tjeneste = SisteOppgaverTjeneste(repository, mockk(), pepClient, pdlService, mockk())

        assertThrows<IllegalArgumentException> {
            tjeneste.lagreSisteOppgave(OppgaveNøkkelDto("oppgave", "type", Områder.AKTIVITETSPENGER), kontekst)
        }

        verify { repository wasNot Called }
    }

    @Test
    fun `skal avvise ressurs med feil område eller oppgavetype før lagring`() {
        val repository = mockk<SisteOppgaverRepository>()
        val oppgaver = mockk<OppgaveRepository>()
        val oppgave = mockk<Oppgave>()
        val tjeneste = SisteOppgaverTjeneste(repository, oppgaver, pepClient, pdlService, get<TransactionalManager>())
        every { oppgaver.hentNyesteOppgaveForEksternId(any(), Områder.K9, "oppgave", any()) } returns oppgave
        every { oppgave.oppgavetype.område.tilOmrådeEnum() } returns Områder.AKTIVITETSPENGER

        assertThrows<IllegalArgumentException> {
            tjeneste.lagreSisteOppgave(OppgaveNøkkelDto("oppgave", "k9sak", Områder.K9), kontekst)
        }
        every { oppgave.oppgavetype.område.tilOmrådeEnum() } returns Områder.K9
        every { oppgave.oppgavetype.eksternId } returns "annen-type"
        assertThrows<IllegalArgumentException> {
            tjeneste.lagreSisteOppgave(OppgaveNøkkelDto("oppgave", "k9sak", Områder.K9), kontekst)
        }

        verify { repository wasNot Called }
    }

    @Test
    fun `skal ikke hente K9 oppgaver i aktivitetspengekontekst`() = runTest {
        val oppgave = OppgaveTestDataBuilder().lagOgLagre()
        tjeneste.lagreSisteOppgave(OppgaveNøkkelDto(oppgave.eksternId, oppgave.oppgavetype.eksternId, Områder.K9), kontekst)

        val resultat = tjeneste.hentSisteOppgaver(TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER))

        assertThat(resultat).hasSize(0)
        coVerify { pepClient wasNot Called }
        coVerify { pdlService wasNot Called }
    }
}
