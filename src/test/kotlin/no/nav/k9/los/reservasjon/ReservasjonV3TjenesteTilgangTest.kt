package no.nav.k9.los.reservasjon

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.ReservasjonsnøkkelOppgaveOppslag
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

class ReservasjonV3TjenesteTilgangTest {

    private val område = Områder.AKTIVITETSPENGER
    private val nøkkel = "nøkkel"
    private val innloggetId = 1L
    private val annenId = 2L

    private val pepClient = mockk<IPepClient>()
    private val saksbehandlerRepository = mockk<SaksbehandlerRepository>()
    private val oppslag = mockk<ReservasjonsnøkkelOppgaveOppslag>()
    private val reservasjonV3Repository = mockk<ReservasjonV3Repository>(relaxed = true)
    private val tx = mockk<TransactionalSession>(relaxed = true)
    private val idToken = mockk<IIdToken>(relaxed = true)
    private val oppgave = mockk<Oppgave> {
        every { hentVerdi("liggerHosBeslutter") } returns null
    }
    private val saksbehandler = mockk<Saksbehandler>(relaxed = true) {
        every { områder } returns listOf(område, Områder.K9)
    }

    private val tjeneste = ReservasjonV3Tjeneste(
        transactionalManager = mockk(relaxed = true),
        reservasjonV3Repository = reservasjonV3Repository,
        pepClient = pepClient,
        saksbehandlerRepository = saksbehandlerRepository,
        reservasjonsnøkkelOppgaveOppslag = oppslag,
        køpåvirkendeHendelseChannel = Channel(Channel.UNLIMITED),
    )

    init {
        every { oppslag.hentÅpneOppgaverForReservasjonsnøkkel(any(), nøkkel, tx) } returns listOf(oppgave)
        every { saksbehandlerRepository.finnSaksbehandlerMedId(any()) } returns saksbehandler
    }

    private fun reserver(reserverForId: Long, utføresAvId: Long, område: Områder = this.område) =
        runBlocking(CoroutineRequestContext(idToken, område)) {
            tjeneste.taReservasjon(
                område = område,
                reservasjonsnøkkel = nøkkel,
                reserverForId = reserverForId,
                utføresAvId = utføresAvId,
                gyldigFra = LocalDateTime.now(),
                gyldigTil = LocalDateTime.now().plusDays(1),
                kommentar = null,
                tx = tx,
            )
        }

    @Test
    fun `reservasjon for seg selv sjekker tilgang med innlogget brukers token`() {
        coEvery { pepClient.harTilgangTilOppgaveV3(område, idToken, oppgave, Action.reserver) } returns true

        reserver(reserverForId = innloggetId, utføresAvId = innloggetId)

        coVerify(exactly = 1) { pepClient.harTilgangTilOppgaveV3(område, idToken, oppgave, Action.reserver) }
        coVerify(exactly = 0) { pepClient.harTilgangTilOppgaveV3(any(), any<Oppgave>(), any<Saksbehandler>(), any()) }
    }

    @Test
    fun `reservasjon for annen saksbehandler i K9 sjekker mottakerens tilgang`() {
        val område = Områder.K9
        coEvery { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) } returns true

        reserver(reserverForId = annenId, utføresAvId = innloggetId, område = område)

        coVerify(exactly = 1) { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) }
        coVerify(exactly = 0) { pepClient.harTilgangTilOppgaveV3(any(), any<IIdToken>(), any(), any()) }
    }

    @Test
    fun `reservasjon for annen saksbehandler på aktivitetspenger sjekker mottakerens tilgang`() {
        coEvery { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) } returns true

        reserver(reserverForId = annenId, utføresAvId = innloggetId)

        coVerify(exactly = 1) { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) }
        coVerify(exactly = 0) { pepClient.harTilgangTilOppgaveV3(any(), any<IIdToken>(), any(), any()) }
    }

    @Test
    fun `reservasjon for andre på aktivitetspenger avvises når mottaker mangler tilgang selv om utfører har tilgang`() {
        coEvery { pepClient.harTilgangTilOppgaveV3(område, idToken, oppgave, Action.reserver) } returns true
        coEvery { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) } returns false

        assertFailsWith<ManglerTilgangException> {
            reserver(reserverForId = annenId, utføresAvId = innloggetId)
        }

        io.mockk.verify(exactly = 0) { reservasjonV3Repository.lagreReservasjon(any(), any(), any()) }
        coVerify(exactly = 1) { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) }
        coVerify(exactly = 0) { pepClient.harTilgangTilOppgaveV3(any(), any<IIdToken>(), any(), any()) }
    }

    @Test
    fun `feil i mottakers tilgangssjekk lagrer ingen reservasjon`() {
        coEvery { pepClient.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) } throws IllegalStateException("PDP utilgjengelig")

        assertFailsWith<IllegalStateException> {
            reserver(reserverForId = annenId, utføresAvId = innloggetId)
        }

        io.mockk.verify(exactly = 0) { reservasjonV3Repository.lagreReservasjon(any(), any(), any()) }
    }
}
