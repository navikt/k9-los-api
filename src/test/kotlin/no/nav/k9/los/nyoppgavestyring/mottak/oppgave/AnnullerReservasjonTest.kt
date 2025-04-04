package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get

@Disabled
class AnnullerReservasjonTest: AbstractK9LosIntegrationTest() {
    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var transactionalManager: TransactionalManager
    private var reservasjonV3Tjenestemock = mockk<ReservasjonV3Tjeneste>()
    private lateinit var oppgavemodellBuilder: RedusertOppgaveTestmodellBuilder

    @BeforeEach
    fun setup() {
        oppgaveV3Tjeneste = OppgaveV3Tjeneste(
            oppgaveV3Repository = get(),
            partisjonertOppgaveRepository = get(),
            oppgavetypeRepository = get(),
            områdeRepository = get(),
        )
        oppgavemodellBuilder = RedusertOppgaveTestmodellBuilder()
        oppgavemodellBuilder.byggOppgavemodell()
        transactionalManager = get()

        justRun { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
    }


    @Test
    fun `hvis alle oppgaver på reservasjonsnøkkel er lukket skal reservasjon annulleres -- kun en oppgave`() {
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgavemodellBuilder.lagOppgaveDto(status = Oppgavestatus.AAPEN.toString()), tx)
        }
        verify(exactly = 0) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgavemodellBuilder.lagOppgaveDto(status = Oppgavestatus.LUKKET.toString()), tx)
        }
        verify(exactly = 1) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
    }

    @Test
    fun `hvis alle oppgaver på reservasjonsnøkkel er lukket skal reservasjon annulleres -- to oppgaver`() {
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgavemodellBuilder.lagOppgaveDto(
                id = "test1",
                status = Oppgavestatus.AAPEN.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgavemodellBuilder.lagOppgaveDto(
                id = "test2",
                status = Oppgavestatus.AAPEN.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgavemodellBuilder.lagOppgaveDto(
                id = "test2",
                status = Oppgavestatus.LUKKET.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        verify(exactly = 0) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgavemodellBuilder.lagOppgaveDto(
                id = "test1",
                status = Oppgavestatus.LUKKET.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        verify(exactly = 1) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
    }
}