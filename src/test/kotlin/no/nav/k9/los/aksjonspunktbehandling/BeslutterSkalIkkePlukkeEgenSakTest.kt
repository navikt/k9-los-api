package no.nav.k9.los.aksjonspunktbehandling

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.modell.AksjonspunktStatus
import no.nav.k9.los.domene.modell.AksjonspunktTilstand
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.Enhet
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.KøSortering
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertSame


class BeslutterSkalIkkePlukkeEgenSakTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `Beslutter skal ikke plukke en oppgave beslutteren har behandlet`() {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()

        val oppgaveTjeneste = lagOppgaveTjenesteMedMocketV3Kobling()
        val oppgave = Oppgave(
            behandlingId = null,
            fagsakSaksnummer = "123456",
            aktorId = "",
            journalpostId = null,
            behandlendeEnhet = "",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now(),
            behandlingStatus = BehandlingStatus.UTREDES,
            behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
            fagsakYtelseType = FagsakYtelseType.OMSORGSPENGER,
            eventTid = LocalDateTime.now(),
            aktiv = true,
            system = "",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = listOf(),
            aksjonspunkter = Aksjonspunkter(
                liste = mapOf("5016" to "OPPR"),
                apTilstander = listOf(AksjonspunktTilstand("5016", AksjonspunktStatus.OPPRETTET, null, null))
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarMedlemskap = false,
            avklarArbeidsforhold = false,
            kode6 = false,
            skjermet = false,
            utenlands = false,
            vurderopptjeningsvilkåret = false,
            ansvarligSaksbehandlerForTotrinn = "B123456",
            ansvarligSaksbehandlerIdent = null
        )
        oppgaveRepository.lagre(oppgave.eksternId) {
            oppgave
        }
        val oppgaveKø = OppgaveKø(
            id = UUID.randomUUID(),
            navn = "Beslutter",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf(),
            skjermet = false,
            oppgaverOgDatoer = mutableListOf(),
            kode6 = false
        )

        val nesteOppgaverIKø = runBlocking {
            oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                oppgave,
                merknader = oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
            )

            oppgaveKøRepository.lagreIkkeTaHensyn(oppgaveKø.id) {
                oppgaveKø
            }

            val nesteOppgaverIKø = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveKø.id)
            nesteOppgaverIKø
        }

        assertSame(1, nesteOppgaverIKø.size )
    }


    private fun lagOppgaveTjenesteMedMocketV3Kobling(): OppgaveTjeneste {
        val oversetterMock = mockk<ReservasjonOversetter>()
        every { oversetterMock.hentAktivReservasjonFraGammelKontekst(any()) } returns null
        every {
            oversetterMock.taNyReservasjonFraGammelKontekst(any(), any(), any(), any(), any())
        } returns ReservasjonV3(
            reservertAv = 123,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null
        )

        return OppgaveTjeneste(
            get<OppgaveRepository>(),
            get<OppgaveRepositoryV2>(),
            get<OppgaveKøRepository>(),
            get<SaksbehandlerRepository>(),
            get<IPdlService>(),
            get<ReservasjonRepository>(),
            get<Configuration>(),
            get<IAzureGraphService>(),
            get<IPepClient>(),
            get<StatistikkRepository>(),
            oversetterMock,
            get(named("statistikkRefreshChannel"))
        )
    }
}
