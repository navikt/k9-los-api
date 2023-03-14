package no.nav.k9.los.tjenester.saksbehandler.oppgave

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertNull
import kotlin.test.asserter

class OppgaveTjenesteTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `hent fagsak`() {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val oppgave1 = Oppgave(

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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }

        runBlocking {
            val fagsaker = oppgaveTjeneste.søkFagsaker("Yz647")
            assert(fagsaker.oppgaver.isNotEmpty())
        }
    }

    @Test
    fun hentReservasjonsHistorikk() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }


        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.size == 1)
        val oppgave = oppgaver[0]

        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "123",
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "ny",
                navn = null,
                epost = "test2@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)
        oppgaveTjeneste.flyttReservasjon(oppgave.eksternId, "ny", "Ville ikke ha oppgaven")
        val reservasjonsHistorikk = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave.eksternId)

        assert(reservasjonsHistorikk.reservasjoner.size == 2)
        assert(reservasjonsHistorikk.reservasjoner[0].flyttetAv == "saksbehandler@nav.no")
    }

    @Test
    fun skalKunnePlukkeSisteSakIenKø() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val brukerIdent = "123"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        // act
        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        // assert
        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.size == 1)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")
    }

    @Test
    fun skalIkkePlukkeEnParSakDerDenAndreSakErReservertPåEnAnnenSaksbehandler() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val brukerIdent = "123"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.size == 1)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")

        val oppgave2 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val hentOppgavekø = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø.id) {
            hentOppgavekø
        }

        val brukerIdent2 = "1337"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent2,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        val oppgaveFraKø = oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent2,
            emptyArray<OppgaveDto>().toMutableList()
        )

        assertNull(oppgaveFraKø)
    }

    @Test
    fun `skal sortere på størrelse på feil utbetalingsbeløp`() = runBlocking {
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val tilbakeKrevingsKø = OppgaveKø(
            id = UUID.randomUUID(),
            navn = "test",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.FEILUTBETALT,
            saksbehandlere = mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn))
        )
        val oppgaveId1 = UUID.randomUUID()
        val oppgaveId2 = UUID.randomUUID()
        val oppgaveId3 = UUID.randomUUID()
        val oppgaveId4 = UUID.randomUUID()


        val o1 = lagOppgave(oppgaveId1, 10000L)
        val o2 = lagOppgave(oppgaveId2, 1000L)
        val o3 = lagOppgave(oppgaveId3, 100L)
        val o4 = lagOppgave(oppgaveId4, 10L)

        lagreOppgaverOgLeggTilIKø(tilbakeKrevingsKø, o2, o3, o4, o1)

        //sjekk at køen er sorter etter høyest feilutbetaling
        val hentOppgaver = oppgaveTjeneste.hentOppgaver(tilbakeKrevingsKø.id)

        assertThat(hentOppgaver[0].feilutbetaltBeløp).isEqualTo(o1.feilutbetaltBeløp)
        assertThat(hentOppgaver[1].feilutbetaltBeløp).isEqualTo(o2.feilutbetaltBeløp)
        assertThat(hentOppgaver[2].feilutbetaltBeløp).isEqualTo(o3.feilutbetaltBeløp)
        assertThat(hentOppgaver[3].feilutbetaltBeløp).isEqualTo(o4.feilutbetaltBeløp)
    }

    @Test
    fun `skal sortere på beslutter ap opprettet tidspunkt ved beslutter kø`() = runBlocking {

        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val beslutterKø = OppgaveKø(
            id = UUID.randomUUID(),
            navn = "test",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringAndreKriterierType = andreKriterierDtos(AndreKriterierType.TIL_BESLUTTER),
            saksbehandlere = mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn))
        )

        val oppgaveId1 = UUID.fromString("0000000-0000-0000-0000-000000000001")
        val oppgaveId2 = UUID.fromString("0000000-0000-0000-0000-000000000002")
        val oppgaveId3 = UUID.fromString("0000000-0000-0000-0000-000000000003")
        val oppgaveId4 = UUID.fromString("0000000-0000-0000-0000-000000000004")
        val oppgaveId5 = UUID.fromString("0000000-0000-0000-0000-000000000005")

        val now = LocalDateTime.now()

        //1
        val o1b1 = lagOppgave(oppgaveId1,
            tilBeslutter = true,
            behandlingOpprettet = now.minusDays(5),
            beslutterApOpprettTid = now.minusDays(5)
        )

        //3
        val o2b3 = lagOppgave(oppgaveId2,
            tilBeslutterTilbakekreving = true,
            behandlingOpprettet = now.minusDays(4),
            beslutterApOpprettTid = now.minusDays(3)
        )

        //5
        val o3b5 = lagOppgave(oppgaveId3,
            tilBeslutter = true,
            behandlingOpprettet = now.minusDays(3),
            beslutterApOpprettTid = now.minusDays(1)
        )

        //4
        val o4b4 = lagOppgave(oppgaveId4,
            tilBeslutter = true,
            behandlingOpprettet = now.minusDays(2)
            //hvis opprettet tid mangler så brukes behandling opprettet
        )

        //2
        val o5b2 = lagOppgave(oppgaveId5,
            tilBeslutter = true,
            behandlingOpprettet = now.minusDays(1),
            beslutterApOpprettTid = now.minusDays(4)
        )

        lagreOppgaverOgLeggTilIKø(beslutterKø, o1b1, o2b3, o3b5, o4b4, o5b2)

        //sjekk at køen er sorter etter eldste beslutter AP
        val hentOppgaver = oppgaveTjeneste.hentOppgaver(beslutterKø.id)

        assertThat(hentOppgaver).extracting { it.eksternId }
            .containsExactly(
                o1b1.eksternId,
                o5b2.eksternId,
                o2b3.eksternId,
                o4b4.eksternId,
                o3b5.eksternId
            )

    }

    @Test
    fun `skal sortere på behandling opprettet tidspunkt default`() = runBlocking {

        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val vanligKø = OppgaveKø(
            id = UUID.randomUUID(),
            navn = "test",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            saksbehandlere = mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn))
        )

        val oppgaveId1 = UUID.fromString("0000000-0000-0000-0000-000000000001")
        val oppgaveId2 = UUID.fromString("0000000-0000-0000-0000-000000000002")
        val oppgaveId3 = UUID.fromString("0000000-0000-0000-0000-000000000003")

        val now = LocalDateTime.now()

        //1
        val o1 = lagOppgave(oppgaveId1,
            behandlingOpprettet = now.minusDays(3),
        )

        //3
        val o2 = lagOppgave(oppgaveId2,
            behandlingOpprettet = now.minusDays(1),
        )

        //2
        val o3 = lagOppgave(oppgaveId3,
            behandlingOpprettet = now.minusDays(2),
            beslutterApOpprettTid = now.minusDays(1)
        )


        lagreOppgaverOgLeggTilIKø(vanligKø, o1, o2, o3)

        val hentOppgaver = oppgaveTjeneste.hentOppgaver(vanligKø.id)

        assertThat(hentOppgaver).extracting { it.eksternId }
            .containsExactly(
                o1.eksternId,
                o3.eksternId,
                o2.eksternId
            )

    }

    private suspend fun lagreOppgaverOgLeggTilIKø(
        beslutterKø: OppgaveKø,
        vararg oppgaver: Oppgave
    ) {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()

        oppgaver.forEach {o ->
            beslutterKø.leggOppgaveTilEllerFjernFraKø(
                o,
                merknader = emptyList()
            )
            oppgaveRepository.lagre(o.eksternId) { o }
            oppgaveKøRepository.lagre(beslutterKø.id) { beslutterKø}
        }

    }


    private fun andreKriterierDtos(andreKriterierType: AndreKriterierType): MutableList<AndreKriterierDto> =
        mutableListOf(AndreKriterierDto("1", andreKriterierType, true, true))

    private fun lagOppgave(
        uuid: UUID, beløp: Long = 0,
        tilBeslutter: Boolean = false,
        tilBeslutterTilbakekreving: Boolean = false,
        behandlingOpprettet: LocalDateTime = LocalDateTime.now().minusDays(23),
        beslutterApOpprettTid: LocalDateTime? = null): Oppgave {
        val apKoder = mutableMapOf("5015" to "OPPR")
        val apTilstand = mutableListOf(AksjonspunktTilstand("5015", AksjonspunktStatus.OPPRETTET))

        if (tilBeslutter) {
            apKoder["5016"] = "OPPR"
            apTilstand.add(AksjonspunktTilstand("5016", AksjonspunktStatus.OPPRETTET, opprettetTidspunkt = beslutterApOpprettTid))
        }

        if (tilBeslutterTilbakekreving) {
            apKoder["5005"] = "OPPR"
            apTilstand.add(AksjonspunktTilstand("5005", AksjonspunktStatus.OPPRETTET, opprettetTidspunkt = beslutterApOpprettTid))
        }

        val aksjonspunkter = Aksjonspunkter(
            apKoder,
            apTilstand
        )


        return Oppgave(
            eksternId = uuid,
            feilutbetaltBeløp = beløp,
            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = behandlingOpprettet,
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = aksjonspunkter,
            tilBeslutter = tilBeslutter || tilBeslutterTilbakekreving,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }

    @Test
    fun skalPlukkeParSakHvisSaksbehandlingHarOpprinneligSakPåSeg() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val brukerIdent = "123"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.size == 1)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")

        val oppgave2 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273852"
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val hentOppgavekø = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø.id) {
            hentOppgavekø
        }

        val oppgave3 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave3.eksternId) { oppgave3 }

        val hentOppgavekø2 = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø2.leggOppgaveTilEllerFjernFraKø(
            oppgave3,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave3.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø2.id) {
            hentOppgavekø2
        }

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk2.reservasjoner.size == 2)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")

        val reservasjonsHistorikk3 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave3.eksternId)
        assert(reservasjonsHistorikk3.reservasjoner.size == 1)
        assert(reservasjonsHistorikk3.reservasjoner[0].reservertAv == "123")
    }

    @Test
    fun skalIkkeFåOppNestesakIListenHvisSaksbehandlerVarBeslutterPåDen() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val brukerIdent = "123"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.size == 1)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")

        val oppgave2 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273852",
            ansvarligBeslutterForTotrinn = "123"
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val hentOppgavekø = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø.id) {
            hentOppgavekø
        }

        val oppgave3 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273851"
        )
        oppgaveRepository.lagre(oppgave3.eksternId) { oppgave3 }

        val hentOppgavekø2 = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø2.leggOppgaveTilEllerFjernFraKø(
            oppgave3,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave3.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø2.id) {
            hentOppgavekø2
        }

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")

        val reservasjonsHistorikk3 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave3.eksternId)
        assert(reservasjonsHistorikk3.reservasjoner.size == 1)
        assert(reservasjonsHistorikk3.reservasjoner[0].reservertAv == "123")
    }

    @Test
    fun skalIkkeFåSammeSakSomDuHarSaksbehandletNårDuSkalBeslutteEnSak() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                liste = mapOf("5016" to "OPPR"),
                apTilstander = listOf(AksjonspunktTilstand("5016", AksjonspunktStatus.OPPRETTET))
            ),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856",
            ansvarligSaksbehandlerForTotrinn = "123"

        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val brukerIdent = "123"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        val oppgave2 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273852",
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val hentOppgavekø = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø.id) {
            hentOppgavekø
        }

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.isEmpty())

        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave2.eksternId)
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")
    }

    @Test
    fun skalIkkeFåSammeSakSomDuHarSaksbehandletNårDuSkalBeslutteEnTilbakekrevingsBehandling() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.TILBAKE, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgaveSomSkalIgnoreres = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.TILBAKE,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = "K9TILBAKE",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                liste = mapOf("5005" to "OPPR"),
                apTilstander = listOf(AksjonspunktTilstand("5005", AksjonspunktStatus.OPPRETTET))
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "null",
            ansvarligSaksbehandlerForTotrinn = "z990404",
            ansvarligSaksbehandlerIdent = "z990404",
            ansvarligBeslutterForTotrinn = "z990404"

        )
        oppgaveRepository.lagre(oppgaveSomSkalIgnoreres.eksternId) { oppgaveSomSkalIgnoreres }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgaveSomSkalIgnoreres,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgaveSomSkalIgnoreres.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val ident = "Z990404"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = ident,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        val oppgaveSomSkalVelges = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.TILBAKE,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = "K9TILBAKE",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273852",
        )
        oppgaveRepository.lagre(oppgaveSomSkalVelges.eksternId) { oppgaveSomSkalVelges }

        val hentOppgavekø = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø.leggOppgaveTilEllerFjernFraKø(
            oppgaveSomSkalVelges,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgaveSomSkalVelges.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø.id) {
            hentOppgavekø
        }

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            ident,
            emptyArray<OppgaveDto>().toMutableList()
        )


        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgaveSomSkalIgnoreres.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.isEmpty())

        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgaveSomSkalVelges.eksternId)
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == ident)
    }

    @Test
    fun skalIkkeFåOppNestesakIListenHvisSaksbehandlerVarBeslutterPåParsaken() = runBlocking {
        // arrange
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val oppgaveKøId = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = oppgaveKøId,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(oppgaveKøId) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val brukerIdent = "123"
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = brukerIdent,
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        val oppgave2 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856",
            ansvarligBeslutterForTotrinn = "123"
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val hentOppgavekø = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø.id) {
            hentOppgavekø
        }

        val oppgave3 = Oppgave(

            fagsakSaksnummer = "Yz642",
            aktorId = "273853",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273851"
        )
        oppgaveRepository.lagre(oppgave3.eksternId) { oppgave3 }

        val hentOppgavekø2 = oppgaveKøRepository.hentOppgavekø(oppgaveKøId)

        hentOppgavekø2.leggOppgaveTilEllerFjernFraKø(
            oppgave3,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave3.eksternId.toString())
        )
        oppgaveKøRepository.lagre(hentOppgavekø2.id) {
            hentOppgavekø2
        }

        oppgaveTjeneste.fåOppgaveFraKø(
            oppgaveKøId.toString(),
            brukerIdent,
            emptyArray<OppgaveDto>().toMutableList()
        )

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        assert(reservasjonsHistorikk1.reservasjoner.isEmpty())

        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave2.eksternId)
        assert(reservasjonsHistorikk2.reservasjoner.isEmpty())

        val reservasjonsHistorikk3 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave3.eksternId)
        assert(reservasjonsHistorikk3.reservasjoner.size == 1)
        assert(reservasjonsHistorikk3.reservasjoner[0].reservertAv == "123")
    }


    @Test
    fun skalKunneReserverToOppgaverSamtidig() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val oppgave2 = Oppgave(
            behandlingId = 9430,
            fagsakSaksnummer = "Yz648",
            aktorId = "273858",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }


        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.size == 2)
        val oppgave = oppgaver[0]

        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "123",
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)
        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave2.eksternId)

        assert(reservasjonsHistorikk1.reservasjoner.size == 1)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")

        val oppgaverEtterRes = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        asserter.assertEquals("Forventer empty liste", 0, oppgaverEtterRes.size)
    }

    @Test
    fun skalKunneReserverEnOppgaveDerEnAnnenErReservertAlt() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()

        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "123",
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(

            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave1.eksternId.toString())
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

            oppgaveTjeneste.reserverOppgave("123", null, oppgave1.eksternId)


        val oppgave2 = Oppgave(
            behandlingId = 9430,
            fagsakSaksnummer = "Yz648",
            aktorId = "273858",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        val hentOppgaveKøer = oppgaveTjeneste.hentOppgaveKøer()
        val oppgaveKø = hentOppgaveKøer[0]

        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }
        oppgaveKø.leggOppgaveTilEllerFjernFraKø(
            oppgave2,
            reservasjonRepository,
            oppgaveRepositoryV2.hentMerknader(oppgave2.eksternId.toString())
        )

        oppgaveKøRepository.lagre(oppgaveKø.id) {
            oppgaveKø
        }

        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveKø.id)
        asserter.assertEquals("Forventer en oppgave her", 1, oppgaver.size)
        val oppgave = oppgaver[0]

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave2.eksternId)

        assert(reservasjonsHistorikk1.reservasjoner.size == 2)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")

        val oppgaverEtterRes = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveKø.id)
        asserter.assertEquals("Forventer empty liste", 0, oppgaverEtterRes.size)
    }

    @Test
    fun skal_bare_returnere_aktivte_eller_sist_ikke_aktive_oppgave() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val fagsakSaksnummer = "Yz647"
        val oppgave1 = Oppgave(
            behandlingId = 9437,
            fagsakSaksnummer = fagsakSaksnummer,
            aktorId = "273857",
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )

        val oppgave2 = Oppgave(

            fagsakSaksnummer = fagsakSaksnummer,
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = false,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val saker = oppgaveTjeneste.søkFagsaker(fagsakSaksnummer)
        assertThat(saker.oppgaver.size).isEqualTo(1)
    }


    @Test
    fun `Skal kunne endre reservert av på eksisterende reservasjon`() = runBlocking {
        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val reservasjonRepository = get<ReservasjonRepository>()

        opprettSaksbehandler("Foo")
        opprettSaksbehandler("Bar")
        val nyOppgave = opprettDummyOppgave()

        oppgaveTjeneste.reserverOppgave(
            ident = "Foo",
            overstyrIdent = null,
            oppgaveUuid = nyOppgave.eksternId,
            overstyrSjekk = false,
        )

        val reservasjon = reservasjonRepository.hent(nyOppgave.eksternId)
        assertThat(reservasjon.reservertAv).isEqualTo("Foo")

        oppgaveTjeneste.endreReservasjonPåOppgave(
            ReservasjonEndringDto(
                oppgaveId = nyOppgave.eksternId.toString(),
                brukerIdent = "Bar"
            )
        )

        val reservasjonEtterEndring = reservasjonRepository.hent(nyOppgave.eksternId)
        assertThat(reservasjonEtterEndring.reservertAv).isEqualTo("Bar")
    }

    @Test
    fun `Skal kunne endre begrunnelse på eksisterende reservasjon`() = runBlocking {
        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val reservasjonRepository = get<ReservasjonRepository>()

        opprettSaksbehandler("Foo")
        val nyOppgave = opprettDummyOppgave()

        oppgaveTjeneste.reserverOppgave(
            ident = "Foo",
            overstyrIdent = null,
            oppgaveUuid = nyOppgave.eksternId,
            overstyrSjekk = false,
        )

        val reservasjon = reservasjonRepository.hent(nyOppgave.eksternId)
        assertThat(reservasjon.begrunnelse).isNull()

        oppgaveTjeneste.endreReservasjonPåOppgave(
            ReservasjonEndringDto(
                oppgaveId = nyOppgave.eksternId.toString(),
                begrunnelse = "test begrunnelse"
            )
        )

        val reservasjonEtterEndring = reservasjonRepository.hent(nyOppgave.eksternId)
        assertThat(reservasjonEtterEndring.begrunnelse).isEqualTo("test begrunnelse")
    }

    @Test
    fun `Skal kunne endre dato på eksisterende reservasjon`() = runBlocking {
        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val reservasjonRepository = get<ReservasjonRepository>()

        opprettSaksbehandler("Foo")
        val nyOppgave = opprettDummyOppgave()

        oppgaveTjeneste.reserverOppgave(
            ident = "Foo",
            overstyrIdent = null,
            oppgaveUuid = nyOppgave.eksternId,
            overstyrSjekk = false,
        )

        val reservasjon = reservasjonRepository.hent(nyOppgave.eksternId)
        assertThat(reservasjon.begrunnelse).isNull()

        val nyDato = reservasjon.reservertTil!!.toLocalDate().plusDays(10)
        oppgaveTjeneste.endreReservasjonPåOppgave(
            ReservasjonEndringDto(
                oppgaveId = nyOppgave.eksternId.toString(),
                reserverTil = nyDato
            )
        )

        val reservasjonEtterEndring = reservasjonRepository.hent(nyOppgave.eksternId)

        //sjekker at datoen har endret seg siden forskyvReservasjonsDato gjør testen sporadisk
        assertThat(reservasjonEtterEndring.reservertTil!!.toLocalDate()).isNotEqualTo(reservasjon.reservertTil!!.toLocalDate())
    }


    private fun opprettDummyOppgave(): Oppgave {
        val oppgaveRepository = get<OppgaveRepository>()

        val oppgave = Oppgave(
            behandlingId = Random().nextLong(),
            fagsakSaksnummer = UUID.randomUUID().toString(),
            aktorId = Random().nextLong().toString(),
            journalpostId = null,
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
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
        return oppgave
    }

    private suspend fun opprettSaksbehandler(ident: String) {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = ident,
                navn = "$ident Testersen",
                epost = "$ident@test.no",
                enhet = null
            )
        )
    }

}
