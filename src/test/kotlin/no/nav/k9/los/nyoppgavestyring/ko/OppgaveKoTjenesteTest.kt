package no.nav.k9.los.nyoppgavestyring.ko

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.query.Avgrensning
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OppgaveKoTjenesteTest {

    @Test
    fun `hentOppgaverFraKø filtrerer med pep uten å hente flere sider`() = runBlocking {
        val oppgaveKoRepository = mockk<OppgaveKoRepository>()
        val oppgaveQueryService = mockk<OppgaveQueryService>()
        val pepClient = mockk<IPepClient>()

        val tjeneste = OppgaveKoTjeneste(
            transactionalManager = mockk<TransactionalManager>(relaxed = true),
            oppgaveKoRepository = oppgaveKoRepository,
            oppgaveQueryService = oppgaveQueryService,
            reservasjonV3Tjeneste = mockk<ReservasjonV3Tjeneste>(relaxed = true),
            saksbehandlerRepository = mockk<SaksbehandlerRepository>(relaxed = true),
            pdlService = mockk<IPdlService>(relaxed = true),
            pepClient = pepClient,
            køpåvirkendeHendelseChannel = Channel(Channel.UNLIMITED),
            feltdefinisjonTjeneste = mockk<FeltdefinisjonTjeneste>(relaxed = true),
        )

        val kø = OppgaveKo(
            id = 1L,
            versjon = 1L,
            tittel = "Testkø",
            beskrivelse = "",
            oppgaveQuery = OppgaveQuery(filtere = emptyList()),
            frittValgAvOppgave = false,
            saksbehandlerIds = emptyList(),
            saksbehandlere = emptyList(),
            endretTidspunkt = null,
            skjermet = false,
        )

        val utenTilgang = oppgave("uten-tilgang", "SAK-1")
        val førsteMedTilgang = oppgave("med-tilgang-1", "SAK-2")

        coEvery { pepClient.harTilgangTilKode6() } returns false
        every { oppgaveKoRepository.hent(1L, false) } returns kø
        every {
            oppgaveQueryService.queryForOppgave(
                QueryRequest(
                    oppgaveQuery = kø.oppgaveQuery,
                    fjernReserverte = false,
                    avgrensning = Avgrensning.maxAntall(2),
                )
            )
        } returns listOf(utenTilgang, førsteMedTilgang)
        coEvery { pepClient.harTilgangTilOppgaveV3(utenTilgang, Action.read, null) } returns false
        coEvery { pepClient.harTilgangTilOppgaveV3(førsteMedTilgang, Action.read, null) } returns true

        val resultat = tjeneste.hentOppgaverFraKø(
            oppgaveKoId = 1L,
            ønsketAntallOppgaver = 2L,
        )

        assertThat(resultat.rader).hasSize(1)
        assertThat(resultat.rader.mapNotNull { it["id"] }).containsExactly("SAK-2")
        coVerify(exactly = 1) {
            oppgaveQueryService.queryForOppgave(
                QueryRequest(
                    oppgaveQuery = kø.oppgaveQuery,
                    fjernReserverte = false,
                    avgrensning = Avgrensning.maxAntall(2),
                )
            )
        }
    }

    private fun oppgave(eksternId: String, saksnummer: String): Oppgave {
        val oppgavetype = mockk<Oppgavetype>()
        every { oppgavetype.eksternId } returns "k9sak"
        every { oppgavetype.område } returns Område(id = 1, eksternId = "K9")
        every { oppgavetype.oppgavebehandlingsUrlTemplate } returns null

        return Oppgave(
            eksternId = eksternId,
            eksternVersjon = "1",
            reservasjonsnøkkel = eksternId,
            oppgavetype = oppgavetype,
            status = "AAPEN",
            endretTidspunkt = LocalDateTime.now(),
            felter = listOf(
                Oppgavefelt(
                    eksternId = "saksnummer",
                    område = "K9",
                    listetype = false,
                    påkrevd = false,
                    verdi = saksnummer,
                    verdiBigInt = null,
                ),
                Oppgavefelt(
                    eksternId = "behandlingTypekode",
                    område = "K9",
                    listetype = false,
                    påkrevd = false,
                    verdi = BehandlingType.FORSTEGANGSSOKNAD.kode,
                    verdiBigInt = null,
                ),
            ),
        )
    }
}
