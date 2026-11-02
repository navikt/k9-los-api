package no.nav.k9.los.domeneadaptere.eventtiloppgave

import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.ung.kodeverk.Fagsystem
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.hendelse.EventHendelse
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.LocalDateTime
import java.util.UUID

class OppgaveOppdatertHandlerTest {
    private val oppgaveRepository = mockk<OppgaveRepository>()
    private val reservasjonV3Tjeneste = mockk<ReservasjonV3Tjeneste>()
    private val tx = mockk<TransactionalSession>()
    private val handler = OppgaveOppdatertHandler(
        oppgaveRepository,
        reservasjonV3Tjeneste,
        mockk(),
        Channel(Channel.UNLIMITED),
    )

    @TestFactory
    fun `annullerer både saksbehandler- og beslutterreservasjon`() = ungSakTilfeller().map { (event, nøkkel, status) ->
        dynamicTest("$nøkkel $status") {
            clearMocks(oppgaveRepository, reservasjonV3Tjeneste)
            val oppgave = lagOppgave(event, status)
            val nøkler = listOf(nøkkel, "${nøkkel}_beslutter")
            every { reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(event.område, any(), any(), null, tx) } returns true

            handler.håndterOppgaveOppdatert(event, oppgave, tx)

            nøkler.forEach {
                verify(exactly = 1) {
                    reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(event.område, it, any(), null, tx)
                }
            }
            verify { oppgaveRepository wasNot Called }
        }
    }

    @TestFactory
    fun `beholder reservasjoner når oppgaven verken er lukket eller på vent`() =
        listOf(Oppgavestatus.AAPEN, Oppgavestatus.UAVKLART).map { status ->
            dynamicTest("$status") {
                clearMocks(oppgaveRepository, reservasjonV3Tjeneste)
                val event = lagEvent(FagsakYtelseType.UNGDOMSYTELSE)

                handler.håndterOppgaveOppdatert(event, lagOppgave(event, status), tx)

                verify { oppgaveRepository wasNot Called }
                verify { reservasjonV3Tjeneste wasNot Called }
            }
        }

    private fun lagOppgave(event: EventLagret.UngSak, oppgavestatus: Oppgavestatus): OppgaveV3 = mockk {
        every { status } returns oppgavestatus
        every { område } returns event.område
        every { eksternId } returns event.eksternId
    }

    companion object {
        private fun ungSakTilfeller(): List<Triple<EventLagret.UngSak, String, Oppgavestatus>> {
            val tilfeller = listOf(
                lagEvent(FagsakYtelseType.UNGDOMSYTELSE) to "UPY_b_del1_1234567890123",
                lagEvent(FagsakYtelseType.UNGDOMSYTELSE, BehandlingType.KLAGE) to "UPY_k_1234567890123",
                lagEvent(
                    FagsakYtelseType.AKTIVITETSPENGER,
                    behandlingSteg = BehandlingStegType.NAV_KONTOR_FORESLÅ_VILKÅR,
                ) to "AKT_b_del1_1234567890123",
                lagEvent(FagsakYtelseType.AKTIVITETSPENGER) to "AKT_b_del2_1234567890123",
            )
            return tilfeller.flatMap { (event, nøkkel) ->
                listOf(Oppgavestatus.LUKKET, Oppgavestatus.VENTER).map { status ->
                    Triple(event, nøkkel, status)
                }
            }
        }

        private fun lagEvent(
            ytelse: FagsakYtelseType,
            behandlingType: BehandlingType = BehandlingType.FØRSTEGANGSSØKNAD,
            behandlingSteg: BehandlingStegType? = null,
        ): EventLagret.UngSak {
            val tidspunkt = LocalDateTime.of(2026, 11, 2, 12, 0)
            val event = UngSakEventDto(
                eksternId = UUID.randomUUID(),
                fagsystem = Fagsystem.UNG_SAK,
                saksnummer = "SAK123",
                aktørId = "1234567890123",
                eventTid = tidspunkt,
                eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
                behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
                behandlingSteg = behandlingSteg?.kode,
                ytelseTypeKode = ytelse.kode,
                behandlingTypeKode = behandlingType.kode,
                opprettetBehandling = tidspunkt.minusDays(1),
            )
            return EventLagret.UngSak(
                nøkkelId = 1L,
                eksternId = event.eksternId.toString(),
                eksternVersjon = tidspunkt.toString(),
                eventJson = LosObjectMapper.instance.writeValueAsString(event),
                opprettet = tidspunkt,
                dirty = false,
            )
        }
    }
}
