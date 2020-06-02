package no.nav.k9.domene.modell

import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModellTest {

    private val uuid = UUID.randomUUID()

    @Test
    fun `Oppgave uten aksjonspunkter`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf()
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        assertEquals(eventDto, modell.sisteEvent())
        assertEquals(eventDto, modell.førsteEvent())

        assertFalse(modell.erTom())
        assertTrue(modell.starterSak())

        val oppgave = modell.oppgave()
        assertFalse(oppgave.tilBeslutter)
        assertFalse(oppgave.skjermet)
        assertFalse(oppgave.aktiv)
        assertEquals("1442456610368", oppgave.aktorId)
        assertEquals("event.behandlendeEnhet", oppgave.behandlendeEnhet)
        assertEquals(1050437, oppgave.behandlingId)
        assertNotEquals(null, oppgave.oppgaveAvsluttet)
        assertEquals(emptyList(), oppgave.oppgaveEgenskap)
    }

    @Test
    fun `Oppgave til beslutter`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.OPPRETTET.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.tilBeslutter)
    }

    @Test
    fun `Oppgave til beslutter UTFØRT`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.UTFØRT.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertFalse(oppgave.tilBeslutter)
    }


    @Test
    fun `Oppgave til beslutter AVBRUTT`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.AVBRUTT.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertFalse(oppgave.tilBeslutter)
    }

    @Test
    fun `Oppgave til skal ha utenlandstildnitt automatisk`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE to AksjonspunktStatus.OPPRETTET.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.utenlands)
    }

    @Test
    fun `Oppgave til skal ha utenlandstildnitt manuell`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE to AksjonspunktStatus.OPPRETTET.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.utenlands)
    }


    @Test
    fun `Oppgave til skal ha årskvantum`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.VURDER_ÅRSKVANTUM_KVOTE to AksjonspunktStatus.OPPRETTET.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.årskvantum)
    }

    @Test
    fun `Oppgave til skal ha avklar medlemskap`() {
        val eventDto = BehandlingProsessEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlinStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.AVKLAR_FORTSATT_MEDLEMSKAP_KODE to AksjonspunktStatus.OPPRETTET.kode)
        )
        val modell = Modell(
            eventer = listOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.avklarMedlemskap)
    }
}