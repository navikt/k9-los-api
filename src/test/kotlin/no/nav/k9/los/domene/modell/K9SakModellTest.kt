package no.nav.k9.los.domene.modell

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakModell
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class K9SakModellTest {

    private val uuid = UUID.randomUUID()

    @Test
    fun `Oppgave uten aksjonspunkter`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        assertEquals(eventDto, modell.sisteEvent())
        assertEquals(eventDto, modell.førsteEvent())

        assertFalse(modell.erTom())
        assertTrue(modell.starterSak())

        val oppgave = modell.oppgave()
        assertFalse(oppgave.tilBeslutter)
        assertFalse(oppgave.kode6)
        assertFalse(oppgave.aktiv)
        assertEquals("1442456610368", oppgave.aktorId)
        assertEquals("", oppgave.behandlendeEnhet)
        assertEquals(1050437, oppgave.behandlingId)
        assertNotEquals(null, oppgave.oppgaveAvsluttet)
        assertEquals(emptyList(), oppgave.oppgaveEgenskap)
    }

    @Test
    fun `Oppgave til beslutter`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.OPPRETTET.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.tilBeslutter)
    }

    @Test
    fun `Oppgave til beslutter UTFØRT`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.UTFØRT.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertFalse(oppgave.tilBeslutter)
    }


    @Test
    fun `Oppgave til beslutter AVBRUTT`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.AVBRUTT.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertFalse(oppgave.tilBeslutter)
    }

    @Test
    fun `Oppgave til skal ha utenlandstildnitt automatisk`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE to AksjonspunktStatus.OPPRETTET.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.utenlands)
    }

    @Test
    fun `Oppgave til skal ha utenlandstildnitt manuell`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE to AksjonspunktStatus.OPPRETTET.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.utenlands)
    }


    @Test
    fun `Oppgave til skal ha årskvantum`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.VURDER_ÅRSKVANTUM_KVOTE to AksjonspunktStatus.OPPRETTET.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.årskvantum)
    }

    @Test
    fun `Oppgave til skal ha avklar medlemskap`() {
        val eventDto = K9SakEventDto(
            eksternId = uuid,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.OMSORGSPENGER.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktKodeDefinisjon.AVKLAR_FORTSATT_MEDLEMSKAP_KODE to AksjonspunktStatus.OPPRETTET.kode),
            aksjonspunktTilstander = emptyList()
        )
        val modell = K9SakModell(
            eventer = mutableListOf(
                eventDto
            )
        )

        val oppgave = modell.oppgave()
        assertTrue(oppgave.avklarMedlemskap)
    }


}