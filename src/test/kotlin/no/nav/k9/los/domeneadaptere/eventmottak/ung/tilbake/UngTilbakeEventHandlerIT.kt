package no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.AvpOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.UngOmrådesetup
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID

class UngTilbakeEventHandlerIT : AbstractK9LosIntegrationTest() {
    @BeforeEach
    fun setup() {
        get<AvpOmrådesetup>().setup()
        get<UngOmrådesetup>().setup()
    }

    @Test
    fun `lagrer og oppdaterer oppgaver for begge områder`() {
        val handler = UngTilbakeEventHandler(get(), get(), get())
        val events = get<EventRepository>()
        val oppgaver = get<OppgaveRepository>()
        val txManager = get<TransactionalManager>()
        val tid = LocalDateTime.of(2026, 3, 5, 12, 0)
        for ((ytelse, område) in listOf(
            FagsakYtelseType.AKTIVITETSPENGER.kode to Områder.AKTIVITETSPENGER,
            FagsakYtelseType.UNGDOMSYTELSE.kode to Områder.UNGDOMSPROGRAMYTELSEN,
        )) {
            val event = UngTilbakeEventDto(
                eksternId = UUID.randomUUID(),
                fagsystem = "UNGTILBAKE",
                saksnummer = "123456",
                aktørId = "1234567890123",
                eventTid = tid,
                eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
                behandlingStatus = "UTRED",
                ytelseTypeKode = ytelse,
                behandlingTypeKode = BehandlingType.TILBAKEKREVING.kode,
                opprettetBehandling = tid.minusDays(1),
                aksjonspunktKoderMedStatusListe = mapOf("5002" to "OPPR"),
                førsteFeilutbetaling = LocalDate.of(2025, 1, 2),
                feilutbetaltBeløp = BigDecimal("1234.56"),
            )
            fun send(dto: UngTilbakeEventDto) = handler.prosesser(
                dto.eksternId.toString(), dto.eventTid.toString(), LosObjectMapper.instance.writeValueAsString(dto)
            )
            send(event)
            val første = txManager.transaction { tx ->
                oppgaver.hentNyesteOppgaveForEksternIdHvisFinnes(tx, område, event.eksternId.toString())
            }
            assertNotNull(første)
            assertEquals(Oppgavestatus.AAPEN, første!!.status)
            assertEquals("1234.56", første.hentVerdi("feilutbetaltBeløp"))
            assertEquals("2025-01-02", første.hentVerdi("førsteFeilutbetalingDato"))
            send(event.copy(eventTid = tid.plusMinutes(1), aksjonspunktKoderMedStatusListe = mapOf("7001" to "OPPR")))
            val siste = txManager.transaction { tx ->
                oppgaver.hentNyesteOppgaveForEksternIdHvisFinnes(tx, område, event.eksternId.toString())
            }
            assertEquals(Oppgavestatus.VENTER, siste!!.status)
            assertFalse(events.hentAlleEventer(Fagsystem.UNGTILBAKE, event.eksternId.toString()).any { it.dirty })
        }
    }

    @Test
    fun `event med feil forblir dirty og er tilgjengelig for vaktmester`() {
        val handler = UngTilbakeEventHandler(get(), get(), get())
        val repository = get<EventRepository>()
        val tid = LocalDateTime.of(2026, 3, 5, 12, 0)
        val event = UngTilbakeEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = "UNGTILBAKE",
            saksnummer = "123456",
            aktørId = "1234567890123",
            eventTid = tid,
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = "UTRED",
            ytelseTypeKode = FagsakYtelseType.AKTIVITETSPENGER.kode,
            behandlingTypeKode = BehandlingType.TILBAKEKREVING.kode,
            opprettetBehandling = tid,
            aksjonspunktKoderMedStatusListe = mapOf("9999" to "OPPR"),
        )
        handler.prosesser(event.eksternId.toString(), tid.toString(), LosObjectMapper.instance.writeValueAsString(event))
        assertTrue(repository.hentAlleEventer(Fagsystem.UNGTILBAKE, event.eksternId.toString()).single().dirty)
        assertTrue(repository.hentAlleEksternIderMedDirtyEventer().any {
            it.fagsystem == Fagsystem.UNGTILBAKE && it.eksternId == event.eksternId.toString()
        })
    }
}
