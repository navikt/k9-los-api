package no.nav.k9.los.reservasjon

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelUtenOmrådeDto
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.AktivOppgaveOppslag
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals

class ReserverForSaksbehandlerTest {
    private val område = Områder.K9
    private val repository = mockk<SaksbehandlerRepository>()
    private val reservasjonstjeneste = mockk<ReservasjonV3Tjeneste>()
    private val oppslag = mockk<AktivOppgaveOppslag>()
    private val oppgave = mockk<Oppgave> {
        every { reservasjonsnøkkel } returns "reservasjonsnøkkel"
    }
    private val params = ReserverForSaksbehandlerDto(OppgaveNøkkelUtenOmrådeDto("oppgave", "type"), "Z999999")
    private val tjeneste = ReservasjonApisTjeneste(
        repository, reservasjonstjeneste, mockk(), mockk(), oppslag, mockk(), mockk()
    )

    private fun saksbehandler(id: Long, skjermet: Boolean, områder: List<Områder> = listOf(område)) =
        Saksbehandler(id, "Z999999", "Test", "test@nav.no", null, områder, skjermet)

    @Test
    fun `mottaker finnes i samme kode6-leir og reserveres av innlogget bruker`() = runBlocking {
        for (kode6 in listOf(false, true)) {
            val innlogget = saksbehandler(1, kode6)
            val mottaker = saksbehandler(2, kode6)
            every { repository.finnSaksbehandlerMedIdent(params.navident, kode6) } returns mottaker
            every { oppslag.hentAktivOppgaveHvisFinnes(område, "oppgave", "type") } returns oppgave
            coEvery {
                reservasjonstjeneste.taReservasjon(område, "reservasjonsnøkkel", 2, 1, null, any(), any())
            } answers {
                val fra = arg<LocalDateTime>(5)
                assertEquals(fra.leggTilDagerHoppOverHelg(2), arg<LocalDateTime>(6))
                mockk<ReservasjonV3>()
            }

            tjeneste.reserverOppgaveForSaksbehandler(område, params, innlogget)

            verify(exactly = 1) { repository.finnSaksbehandlerMedIdent(params.navident, kode6) }
        }
        coVerify(exactly = 2) {
            reservasjonstjeneste.taReservasjon(område, "reservasjonsnøkkel", 2, 1, null, any(), any())
        }
    }

    @Test
    fun `manglende mottaker avvises uten oppslag i annen kode6-leir`() = runBlocking {
        for (kode6 in listOf(false, true)) {
            every { repository.finnSaksbehandlerMedIdent(params.navident, kode6) } returns null
            assertThrows<FinnerIkkeDataException> {
                tjeneste.reserverOppgaveForSaksbehandler(område, params, saksbehandler(1, kode6))
            }
            verify(exactly = 1) { repository.finnSaksbehandlerMedIdent(params.navident, kode6) }
        }
        verify(exactly = 0) { oppslag.hentAktivOppgaveHvisFinnes(any(), any(), any()) }
    }

    @Test
    fun `mottaker utenfor området avvises`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent(params.navident, false) } returns
            saksbehandler(2, false, listOf(Områder.AKTIVITETSPENGER))
        assertThrows<FinnerIkkeDataException> {
            tjeneste.reserverOppgaveForSaksbehandler(område, params, saksbehandler(1, false))
        }
        coVerify(exactly = 0) { reservasjonstjeneste.taReservasjon(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `manglende oppgave avvises`() = runBlocking {
        klargjør()
        every { oppslag.hentAktivOppgaveHvisFinnes(område, "oppgave", "type") } returns null
        assertThrows<FinnerIkkeDataException> {
            tjeneste.reserverOppgaveForSaksbehandler(område, params, saksbehandler(1, false))
        }
        coVerify(exactly = 0) { reservasjonstjeneste.taReservasjon(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reservasjon hos andre gir konflikt uten overføring`() = runBlocking<Unit> {
        klargjør()
        coEvery { reservasjonstjeneste.taReservasjon(any(), any(), any(), any(), any(), any(), any()) } throws
            AlleredeReservertException("Allerede reservert")
        every { reservasjonstjeneste.finnAktivReservasjon(område, "reservasjonsnøkkel") } returns mockk {
            every { reservertAv } returns 3
        }
        assertThrows<AlleredeReservertException> {
            tjeneste.reserverOppgaveForSaksbehandler(område, params, saksbehandler(1, false))
        }
    }

    @Test
    fun `gjentatt tildeling til samme mottaker lykkes uten å endre reservasjonen`() = runBlocking {
        klargjør()
        coEvery { reservasjonstjeneste.taReservasjon(any(), any(), any(), any(), any(), any(), any()) } throws
            AlleredeReservertException("Allerede reservert")
        every { reservasjonstjeneste.finnAktivReservasjon(område, "reservasjonsnøkkel") } returns mockk {
            every { reservertAv } returns 2
        }
        tjeneste.reserverOppgaveForSaksbehandler(område, params, saksbehandler(1, false))
    }

    @Test
    fun `mottaker uten tilgang avvises`() = runBlocking<Unit> {
        klargjør()
        coEvery { reservasjonstjeneste.taReservasjon(any(), any(), any(), any(), any(), any(), any()) } throws
            ManglerTilgangException("Mangler tilgang")
        assertThrows<ManglerTilgangException> {
            tjeneste.reserverOppgaveForSaksbehandler(område, params, saksbehandler(1, false))
        }
    }

    private fun klargjør() {
        every { repository.finnSaksbehandlerMedIdent(params.navident, false) } returns saksbehandler(2, false)
        every { oppslag.hentAktivOppgaveHvisFinnes(område, "oppgave", "type") } returns oppgave
    }
}
