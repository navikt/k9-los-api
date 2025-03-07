package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import io.mockk.every
import io.mockk.mockk
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus
import no.nav.k9.los.domene.modell.AksjonspunktTilstand
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.repository.NøkkeltallRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class NokkeltallTjenesteTest : AbstractK9LosIntegrationTest() {

    @BeforeEach
    fun beforeEach() {
        get<OmrådeSetup>().setup()
        get<K9SakTilLosAdapterTjeneste>().setup()
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave uten aksjonspunkt skal gi 0 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()
        val områdeRepository = get<OmrådeRepository>()
        områdeRepository.lagre("K9")
        val oppgavetypeRepository = get<OppgavetypeRepository>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(Aksjonspunkter(mapOf()))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent().påVent

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som ikke er autopunkt skal gi 0 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()
        val områdeRepository = get<OmrådeRepository>()
        områdeRepository.lagre("K9")

        //Lag en oppgave med aksjonspunkt
        opprettOppgave(Aksjonspunkter(mapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to "OPPR")))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent().påVent

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som er autopunkt skal gi 1 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()
        val områdeRepository = get<OmrådeRepository>()
        områdeRepository.lagre("K9")

        //Lag en oppgave med autopunkt
        val apKode = AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.kode
        opprettOppgave(Aksjonspunkter(
            liste = mapOf(apKode to "OPPR"),
            apTilstander = listOf(AksjonspunktTilstand(apKode, AksjonspunktStatus.OPPRETTET, Venteårsak.AVV_DOK.kode, frist = LocalDateTime.now().plusDays(1))))
        )

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent().påVent

        assert(oppgaverPåVent.size == 1)
        assert(oppgaverPåVent[0].behandlingType == BehandlingType.FORSTEGANGSSOKNAD)
        assert(oppgaverPåVent[0].fagsakYtelseType == FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        assert(oppgaverPåVent[0].antall == 1)
    }

    @Test
    fun `Hent løste aksjonspunkter bortsett fra de med behandlende enhet 2103`() {
        val statistikkRepository = mockk<StatistikkRepository>()
        val nøkkeltallRepository = mockk<NøkkeltallRepository>()
        val nøkkeltallRepositoryV3 = mockk<NøkkeltallRepositoryV3>()
        val nøkkeltallTjeneste = NokkeltallTjeneste(mockk(), mockk(), mockk(), statistikkRepository, nøkkeltallRepository, nøkkeltallRepositoryV3, KoinProfile.PROD)

        val ferdigstiltBehandling = FerdigstiltBehandling(
            dato = LocalDate.now(),
            fagsakYtelseType = "PSB",
            behandlingType = "BT-002",
            fagsystemType = "K9SAK",
            saksbehandler = "Z12345",
            behandlendeEnhet = "4565 OK AVDELING"
        )

        every { statistikkRepository.hentFerdigstiltOppgavehistorikk(any()) } returns listOf(
            ferdigstiltBehandling,
            ferdigstiltBehandling.copy(behandlendeEnhet = "2103 VIKEN")
        )

        val ferdigstilteoppgaver = nøkkeltallTjeneste.hentFerdigstilteBehandlingerPrEnhetHistorikk()

        assertThat(ferdigstilteoppgaver).containsOnly(ferdigstiltBehandling.dato to mapOf("4565 OK AVDELING" to 1))
        assertThat(ferdigstilteoppgaver).doesNotContain(ferdigstiltBehandling.dato to mapOf("2103 VIKEN" to 1))
    }

    private fun opprettOppgave(aksjonspunkter: Aksjonspunkter) {
        val oppgaveRepo = get<OppgaveRepository>()
        val oppgave = mockOppgave().copy(aksjonspunkter = aksjonspunkter)
        oppgaveRepo.lagre(oppgave.eksternId) {oppgave}

        val oppgaveV3Dto = mockOppgaveV3Dto(oppgave.eksternId, aksjonspunkter)

        val oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            session -> session.transaction {
                tx ->
                    val oppgaveV3 = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveV3Dto, tx)
                    AktivOppgaveRepository.ajourholdAktivOppgave(oppgaveV3!!, 1L, tx)

            }
        }
    }

    private fun mockOppgave(): Oppgave {
        return Oppgave(
            eksternId = UUID.randomUUID(),
            fagsakSaksnummer = "Yz647",
            journalpostId = null,
            aktorId = "273857",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            ansvarligSaksbehandlerIdent = "Z123523",
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }

    private fun mockOppgaveV3Dto(eksternId : UUID, aksjonspunkter: Aksjonspunkter) : OppgaveDto {
        val felter = mutableListOf<OppgaveFeltverdiDto>()

        var status = Oppgavestatus.AAPEN
        aksjonspunkter.liste.forEach {
            val apKode = it.key
            val apTilstand = aksjonspunkter.apTilstander.filter { it.aksjonspunktKode == apKode }.firstOrNull()
            felter.add(OppgaveFeltverdiDto("aktivtAksjonspunkt", apKode))
            if (apTilstand != null) {
                felter.add(OppgaveFeltverdiDto("aktivVenteårsak", apTilstand.venteårsak!!))
                felter.add(OppgaveFeltverdiDto("aktivVentefrist", apTilstand.frist!!.toString()))
                status = Oppgavestatus.VENTER
            }
        }

        felter.add(OppgaveFeltverdiDto("behandlingUuid", eksternId.toString()))
        felter.add(OppgaveFeltverdiDto("aktorId", "273857"))
        felter.add(OppgaveFeltverdiDto("fagsystem", "k9-sak"))
        felter.add(OppgaveFeltverdiDto("saksnummer", "Yz647"))
        felter.add(OppgaveFeltverdiDto("resultattype", BehandlingResultatType.IKKE_FASTSATT.kode))
        felter.add(OppgaveFeltverdiDto("ytelsestype", "PSB"))
        felter.add(OppgaveFeltverdiDto("behandlingsstatus", BehandlingStatus.OPPRETTET.kode))
        felter.add(OppgaveFeltverdiDto("behandlingTypekode", no.nav.k9.kodeverk.behandling.BehandlingType.FØRSTEGANGSSØKNAD.kode))
        felter.add(OppgaveFeltverdiDto("totrinnskontroll", "false"))
        felter.add(OppgaveFeltverdiDto("utenlandstilsnitt", "false"))
        felter.add(OppgaveFeltverdiDto("avventerSøker", "false"))
        felter.add(OppgaveFeltverdiDto("avventerArbeidsgiver", "false"))
        felter.add(OppgaveFeltverdiDto("avventerAnnet", "false"))
        felter.add(OppgaveFeltverdiDto("avventerSaksbehandler",  (status == Oppgavestatus.AAPEN).toString()))
        felter.add(OppgaveFeltverdiDto("avventerTekniskFeil", "false"))
        felter.add(OppgaveFeltverdiDto("avventerAnnetIkkeSaksbehandlingstid", "false"))
        felter.add(OppgaveFeltverdiDto("helautomatiskBehandlet", "false"))

        return OppgaveDto(
            id = eksternId.toString(),
            versjon = LocalDate.now().toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9sak",
            status = status.kode,
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "foo",
            feltverdier = felter
        )
    }
}
