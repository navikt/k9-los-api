package no.nav.k9.repository

import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import no.nav.k9.integrasjon.pdl.PdlService
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertTrue

class OppgaveRepositoryTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig())
    }

    @Test
    fun `Skal deserialisere`() {

        val queryRequest = PdlService.QueryRequest(
            getStringFromResource("/pdl/hentPerson.graphql"),
            mapOf("ident" to "Attributt.ident.value")
        )

        println(objectMapper().writeValueAsString(queryRequest))

    }

    @Test
    fun `Skal hente aktive oppgaver`() {
        val oppgaveRepository = get<OppgaveRepository>()

        lagreOppgave(
            oppgaveRepository,
            Fagsystem.K9SAK.kode,
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            BehandlingType.FORSTEGANGSSOKNAD
        )

        lagreOppgave(
            oppgaveRepository,
            Fagsystem.PUNSJ.kode,
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            BehandlingType.DIGITAL_ETTERSENDELSE
        )

        lagreOppgave(
            oppgaveRepository,
            Fagsystem.K9TILBAKE.kode,
            FagsakYtelseType.OMSORGSDAGER,
            BehandlingType.FORSTEGANGSSOKNAD
        )

        val oppgaver =
            oppgaveRepository.hentAktiveOppgaverGruppertPåFagsystemFagsakytelseOgBehandlingstype()

        assertTrue(oppgaver.size == 3)

    }

    private fun lagreOppgave(
        oppgaveRepository: OppgaveRepository,
        system: String,
        fagsakYtelseType: FagsakYtelseType,
        behandlingType: BehandlingType
    ) {
        val oppgave = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "Yz647",
            journalpostId = null,
            aktorId = "273857",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = behandlingType,
            fagsakYtelseType = fagsakYtelseType,
            aktiv = true,
            system = system,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        oppgaveRepository.lagre(oppgave.eksternId) { oppgave }
    }

    fun getStringFromResource(path: String) =
        OppgaveRepositoryTest::class.java.getResourceAsStream(path).bufferedReader().use { it.readText() }
}
