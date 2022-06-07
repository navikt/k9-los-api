package no.nav.k9.aksjonspunktbehandling

import kotlinx.coroutines.runBlocking
import no.nav.k9.AbstractK9LosIntegrationTest
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.modell.AksjonspunktStatus
import no.nav.k9.domene.modell.AksjonspunktTilstand
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.Enhet
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.KøSortering
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.junit.jupiter.api.Test
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

        val oppgaveTjeneste = get<OppgaveTjeneste>()
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

}
