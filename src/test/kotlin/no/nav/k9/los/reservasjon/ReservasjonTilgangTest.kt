package no.nav.k9.los.reservasjon

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.ReservasjonsnøkkelOppgaveOppslag
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReservasjonTilgangTest {
    private val kontekst = TestKontekstFactory.brukerkontekst(Områder.K9)
    private val utfører = Saksbehandler(1, kontekst.navIdent, "Utfører", "utforer@nav.no", null, listOf(Områder.K9), false)
    private val mottaker = Saksbehandler(2, "Z222222", "Mottaker", "mottaker@nav.no", null, listOf(Områder.K9), false)
    private val saksbehandlere = mockk<SaksbehandlerRepository>()
    private val pep = mockk<IPepClient>()
    private val oppslag = mockk<ReservasjonsnøkkelOppgaveOppslag>()
    private val repository = mockk<ReservasjonV3Repository>()
    private val tjeneste = spyk(ReservasjonV3Tjeneste(mockk(), repository, pep, saksbehandlere, oppslag, mockk()))
    private val sammendrag = mockk<OppgaveSammendragDtoBuilder>()
    private val dtoBuilder = mockk<ReservasjonV3DtoBuilder>()
    private val api = ReservasjonApisTjeneste(saksbehandlere, tjeneste, mockk(), dtoBuilder, mockk(), pep, sammendrag)

    private fun reservasjon(nøkkel: String = "nokkel", område: Områder = Områder.K9, eier: Long = utfører.id): ReservasjonV3 {
        val nå = LocalDateTime.now()
        return ReservasjonV3(eier, nøkkel, "", nå, nå.plusDays(1), null, område)
    }

    private fun oppgave(område: Områder = Områder.K9): Oppgave = mockk<Oppgave>().also {
        every { it.oppgavetype.område.eksternId } returns område.eksternId
        every { it.oppgavetype.område.tilOmrådeEnum() } returns område
        every { it.hentVerdi("liggerHosBeslutter") } returns "false"
    }

    private fun tillat(nøkkel: String = "nokkel", eier: Saksbehandler = utfører): Oppgave {
        val oppgave = oppgave()
        every { tjeneste.finnAktivReservasjon(nøkkel) } returns reservasjon(nøkkel, eier = eier.id)
        every { oppslag.hentÅpneOppgaverForReservasjonsnøkkel(nøkkel) } returns listOf(oppgave)
        every { saksbehandlere.finnSaksbehandlerMedId(eier.id) } returns eier
        coEvery { pep.harTilgangTilOppgaveV3(oppgave, any(), any()) } returns true
        return oppgave
    }

    @Test
    fun `direkte nøkkel fra annet område avvises før forlengelse`() = runTest {
        every { tjeneste.finnAktivReservasjon("nokkel") } returns reservasjon(område = Områder.AKTIVITETSPENGER)

        shouldThrow<ManglerTilgangException> {
            api.forlengReservasjon(ForlengReservasjonDto(null, "nokkel", null, null), utfører, kontekst)
        }

        verify(exactly = 0) { tjeneste.forlengReservasjon(any(), any(), any(), any()) }
        verify { repository wasNot Called }
    }

    @Test
    fun `nøkkel som dekker flere områder avvises før mutasjon`() = runTest {
        tillat()
        every { oppslag.hentÅpneOppgaverForReservasjonsnøkkel("nokkel") } returns listOf(oppgave(), oppgave(Områder.AKTIVITETSPENGER))

        shouldThrow<ManglerTilgangException> {
            api.forlengReservasjon(ForlengReservasjonDto(null, "nokkel", null, null), utfører, kontekst)
        }

        verify(exactly = 0) { tjeneste.forlengReservasjon(any(), any(), any(), any()) }
    }

    @Test
    fun `hele endringsbatchen kontrolleres før første sideeffekt`() = runTest {
        tillat("tillatt")
        every { tjeneste.finnAktivReservasjon("nektet") } returns reservasjon("nektet", Områder.AKTIVITETSPENGER)

        shouldThrow<ManglerTilgangException> {
            api.endreReservasjoner(listOf(ReservasjonEndringDto(null, "tillatt"), ReservasjonEndringDto(null, "nektet")), utfører, false, kontekst)
        }

        verify(exactly = 0) { tjeneste.endreReservasjon(any(), any(), any(), any(), any()) }
        verify { repository wasNot Called }
    }

    @Test
    fun `hele annulleringsbatchen kontrolleres før første sideeffekt`() = runTest {
        tillat("tillatt")
        every { tjeneste.finnAktivReservasjon("nektet") } returns reservasjon("nektet", Områder.AKTIVITETSPENGER)

        shouldThrow<ManglerTilgangException> {
            api.annullerReservasjoner(listOf(AnnullerReservasjonDto(null, "tillatt"), AnnullerReservasjonDto(null, "nektet")), utfører, kontekst)
        }

        verify(exactly = 0) { tjeneste.annullerReservasjonHvisFinnes(any(), any(), any()) }
        verify { repository wasNot Called }
    }

    @Test
    fun `oppgavestyrer uten reserveringsrett kan administrere andres reservasjon`() = runTest {
        val oppgave = tillat(eier = mottaker)
        val leder = kontekst.copy(harTilgangTilReserveringAvOppgaver = false)

        tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, leder)

        coVerify(exactly = 1) { pep.harTilgangTilOppgaveV3(oppgave, leder, Action.read) }
        coVerify(exactly = 0) { pep.harTilgangTilOppgaveV3(any(), any(), Action.reserver) }
    }

    @Test
    fun `saksbehandler kan ikke endre andres reservasjon uten oppgavestyring`() = runTest {
        tillat(eier = mottaker)

        shouldThrow<ManglerTilgangException> {
            tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, kontekst.copy(erOppgavestyrer = false))
        }
    }

    @Test
    fun `egen reservasjon krever reserveringsrett ved forlengelse men ikke oppheving`() = runTest {
        val oppgave = tillat()
        val egen = kontekst.copy(erOppgavestyrer = false, harTilgangTilReserveringAvOppgaver = false)

        shouldThrow<ManglerTilgangException> { tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, egen) }
        tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, egen, oppheving = true)

        coVerify { pep.harTilgangTilOppgaveV3(oppgave, egen, Action.read) }
    }

    @Test
    fun `egen reservasjon kan forlenges med reserveringstilgang`() = runTest {
        val oppgave = tillat()
        val egen = kontekst.copy(erOppgavestyrer = false)

        tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, egen)

        coVerify { pep.harTilgangTilOppgaveV3(oppgave, egen, Action.reserver) }
    }

    @Test
    fun `utførerens PEP avslag stopper mutasjon`() = runTest {
        val oppgave = tillat()
        coEvery { pep.harTilgangTilOppgaveV3(oppgave, kontekst, Action.read) } returns false

        shouldThrow<ManglerTilgangException> {
            api.forlengReservasjon(ForlengReservasjonDto(null, "nokkel", null, null), utfører, kontekst)
        }

        verify(exactly = 0) { tjeneste.forlengReservasjon(any(), any(), any(), any()) }
    }

    @Test
    fun `mottaker må ha område og PEP reservering selv når utfører er oppgavestyrer`() = runTest {
        val oppgave = tillat()
        val utenOmråde = Saksbehandler(2, "Z222222", "Mottaker", "mottaker@nav.no", null, listOf(Områder.AKTIVITETSPENGER), false)
        shouldThrow<ManglerTilgangException> { tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, kontekst, utenOmråde) }

        every { saksbehandlere.finnSaksbehandlerMedId(mottaker.id) } returns mottaker
        coEvery { pep.harTilgangTilOppgaveV3(oppgave, Områder.K9, mottaker, Action.reserver) } returns false
        shouldThrow<ManglerTilgangException> { tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, kontekst, mottaker) }

        coEvery { pep.harTilgangTilOppgaveV3(oppgave, Områder.K9, mottaker, Action.reserver) } returns true
        tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, kontekst, mottaker)
    }

    @Test
    fun `mottaker kan ikke beslutte egen behandling`() = runTest {
        val oppgave = tillat()
        every { saksbehandlere.finnSaksbehandlerMedId(mottaker.id) } returns mottaker
        every { oppgave.hentVerdi("liggerHosBeslutter") } returns "true"
        every { oppgave.hentVerdi("ansvarligSaksbehandler") } returns mottaker.navident

        shouldThrow<ManglerTilgangException> { tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, kontekst, mottaker) }
    }

    @Test
    fun `global kode6 må samsvare for mottaker`() = runTest {
        tillat()
        val kode6 = Saksbehandler(2, "Z222222", "Mottaker", "mottaker@nav.no", null, listOf(Områder.K9, Områder.AKTIVITETSPENGER), true)

        shouldThrow<ManglerTilgangException> { tjeneste.kontrollerReservasjonstilgang("nokkel", utfører, kontekst, kode6) }
    }

    @Test
    fun `sammendrag bygges bare for tillatte oppgaver i valgt område`() = runTest {
        val tillatt = oppgave()
        val nektet = oppgave()
        val annetOmråde = oppgave(Områder.AKTIVITETSPENGER)
        every { tjeneste.hentReservasjonerForSaksbehandler(utfører.id, Områder.K9) } returns listOf(
            ReservasjonV3MedOppgaver(reservasjon(), listOf(tillatt, nektet, annetOmråde)),
            ReservasjonV3MedOppgaver(reservasjon("annen", Områder.AKTIVITETSPENGER), listOf(annetOmråde)),
        )
        coEvery { pep.harTilgangTilOppgaveV3(tillatt, kontekst, Action.read) } returns true
        coEvery { pep.harTilgangTilOppgaveV3(nektet, kontekst, Action.read) } returns false
        val dto = mockk<OppgaveSammendragDto>()
        coEvery { sammendrag.bygg(listOf(tillatt), kontekst, emptyMap()) } returns listOf(dto)

        val resultat = api.hentReserverteOppgaverSammendragForSaksbehandler(utfører, kontekst)

        assertThat(resultat.single().oppgaver).isEqualTo(listOf(dto))
        coVerify(exactly = 0) { pep.harTilgangTilOppgaveV3(annetOmråde, any(), any()) }
        coVerify(exactly = 1) { sammendrag.bygg(listOf(tillatt), kontekst, emptyMap()) }
    }

    @Test
    fun `legacy liste og administrasjonsliste utelater nektede og fremmede reservasjoner`() = runTest {
        val nektet = oppgave()
        val fremmed = oppgave(Områder.AKTIVITETSPENGER)
        val reservasjoner = listOf(
            ReservasjonV3MedOppgaver(reservasjon(), listOf(nektet)),
            ReservasjonV3MedOppgaver(reservasjon("annen", Områder.AKTIVITETSPENGER), listOf(fremmed)),
        )
        every { tjeneste.hentReservasjonerForSaksbehandler(utfører.id, Områder.K9) } returns reservasjoner
        every { tjeneste.hentAlleAktiveReservasjoner(Områder.K9) } returns reservasjoner
        coEvery { pep.harTilgangTilOppgaveV3(nektet, kontekst, Action.read) } returns false

        assertThat(api.hentReserverteOppgaverForSaksbehandler(utfører, kontekst)).isEmpty()
        assertThat(api.hentAlleAktiveReservasjoner(kontekst)).isEmpty()

        coVerify { dtoBuilder wasNot Called }
        coVerify(exactly = 0) { pep.harTilgangTilOppgaveV3(fremmed, any(), any()) }
    }

    @Test
    fun `egen reservasjon uten åpne oppgaver beholdes uten fritekst eller persondata`() = runTest {
        val skjult = reservasjon().copy(id = 1, kommentar = "Fritekst som ikke skal eksponeres", endretAv = mottaker.id)
        every { tjeneste.hentReservasjonerForSaksbehandler(utfører.id, Områder.K9) } returns
            listOf(ReservasjonV3MedOppgaver(skjult, emptyList()))
        val pdl = mockk<IPdlService>()
        val api = ReservasjonApisTjeneste(
            saksbehandlere, tjeneste, mockk(), ReservasjonV3DtoBuilder(pdl, saksbehandlere, pep), mockk(), pep, sammendrag,
        )
        coEvery { sammendrag.bygg(emptyList(), kontekst, emptyMap()) } returns emptyList()

        val legacy = api.hentReserverteOppgaverForSaksbehandler(utfører, kontekst).single()
        val ny = api.hentReserverteOppgaverSammendragForSaksbehandler(utfører, kontekst).single()

        assertThat(legacy.reserverteV3Oppgaver).isEmpty()
        assertThat(legacy.reservasjonsnøkkel).isEqualTo(skjult.reservasjonsnøkkel)
        assertThat(legacy.reservertAvId).isEqualTo(utfører.id)
        assertThat(legacy.reservertTil).isEqualTo(skjult.gyldigTil)
        assertThat(legacy.kommentar).isEqualTo("")
        assertThat(legacy.endretAvNavn).isEqualTo(null)
        assertThat(ny.oppgaver).isEmpty()
        assertThat(ny.reservasjonsnøkkel).isEqualTo(skjult.reservasjonsnøkkel)
        assertThat(ny.kommentar).isEqualTo("")
        assertThat(ny.endretAvNavn).isEqualTo(null)
        coVerify { pdl wasNot Called }
        coVerify { pep wasNot Called }
        verify { saksbehandlere wasNot Called }
    }

    @Test
    fun `tomme reservasjoner gir ikke tilgang til andres metadata eller andre områder`() = runTest {
        val reservasjoner = listOf(
            ReservasjonV3MedOppgaver(reservasjon(eier = mottaker.id), emptyList()),
            ReservasjonV3MedOppgaver(reservasjon(område = Områder.AKTIVITETSPENGER), emptyList()),
        )
        every { tjeneste.hentReservasjonerForSaksbehandler(utfører.id, Områder.K9) } returns reservasjoner
        every { tjeneste.hentReservasjonerForSaksbehandler(mottaker.id, Områder.K9) } returns reservasjoner
        every { tjeneste.hentAlleAktiveReservasjoner(Områder.K9) } returns reservasjoner
        coEvery { sammendrag.bygg(emptyList(), kontekst, emptyMap()) } returns emptyList()

        assertThat(api.hentReserverteOppgaverForSaksbehandler(utfører, kontekst)).isEmpty()
        assertThat(api.hentReserverteOppgaverForSaksbehandler(mottaker, kontekst)).isEmpty()
        assertThat(api.hentReserverteOppgaverSammendragForSaksbehandler(mottaker, kontekst)).isEmpty()
        assertThat(api.hentAlleAktiveReservasjoner(kontekst)).isEmpty()
        coVerify { dtoBuilder wasNot Called }
        coVerify { pep wasNot Called }
    }

    @Test
    fun `egen tom reservasjon krever basistilgang område og riktig skjerming`() = runTest {
        every { tjeneste.hentReservasjonerForSaksbehandler(utfører.id, Områder.K9) } returns
            listOf(ReservasjonV3MedOppgaver(reservasjon(), emptyList()))
        val utenOmråde = Saksbehandler(utfører.id, utfører.navident, utfører.navn, utfører.epost, null, emptyList(), false)

        shouldThrow<ManglerTilgangException> {
            api.hentReserverteOppgaverForSaksbehandler(utfører, kontekst.copy(harBasisTilgang = false))
        }
        assertThat(api.hentReserverteOppgaverForSaksbehandler(utenOmråde, kontekst)).isEmpty()
        assertThat(api.hentReserverteOppgaverForSaksbehandler(utfører, kontekst.copy(harTilgangTilKode6 = true))).isEmpty()
        coVerify { dtoBuilder wasNot Called }
        coVerify { pep wasNot Called }
    }

    @Test
    fun `legacy builder henter ikke persondata for nektet eller fremmed oppgave`() = runTest {
        val pdl = mockk<IPdlService>()
        val nektet = oppgave()
        val fremmed = oppgave(Områder.AKTIVITETSPENGER)
        coEvery { pep.harTilgangTilOppgaveV3(nektet, kontekst, Action.read) } returns false
        val builder = ReservasjonV3DtoBuilder(pdl, saksbehandlere, pep)

        val resultat = builder.byggReservasjonV3Dto(ReservasjonV3MedOppgaver(reservasjon(), listOf(nektet, fremmed)), utfører, kontekst)

        assertThat(resultat.reserverteV3Oppgaver).isEmpty()
        coVerify { pdl wasNot Called }
        coVerify(exactly = 0) { pep.harTilgangTilOppgaveV3(fremmed, any(), any()) }
    }

    @Test
    fun `reservering avviser body fra annet område før oppslag`() = runTest {
        shouldThrow<IllegalArgumentException> {
            api.reserverOppgave(utfører, OppgaveNøkkelDto("oppgave", "type", Områder.AKTIVITETSPENGER), false, kontekst)
        }
        verify { repository wasNot Called }
    }
}
