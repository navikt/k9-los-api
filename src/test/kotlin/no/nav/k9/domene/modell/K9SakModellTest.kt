package no.nav.k9.domene.modell

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.Test
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


class K9SakModellReservasjonTest {

    private val AVBR = AksjonspunktStatus.AVBRUTT.kode
    private val OPPR = AksjonspunktStatus.OPPRETTET.kode
    private val UTFO = AksjonspunktStatus.UTFØRT.kode



    private val uuid = UUID.randomUUID()

    @Test
    fun `Fjerner reservasjon hvis har blitt beslutter`() {
        fjernReservasjonHvisTilBeslutter(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        fjernReservasjonHvisTilBeslutter(FagsakYtelseType.OMSORGSPENGER)
    }

    private fun fjernReservasjonHvisTilBeslutter(fagsakYtelseType: FagsakYtelseType) {
        val foreslåVedtak = mutableMapOf(
            "5015" to UTFO,
            "9001" to UTFO,
            "9203" to AVBR
        )
        val tilBeslutter = mutableMapOf(
            "5015" to UTFO,
            "5016" to OPPR,
            "9001" to UTFO,
            "9203" to AVBR
        )

        val eventBuilder = eventBuilder(fagsakYtelseType)
        val modell = kjørEventer(eventBuilder, foreslåVedtak, tilBeslutter)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue()

    }

    @Test
    fun `Fjerner reservasjon hvis går til vent på PSB`() {
        val legeærklæring = mutableMapOf(
            "9001" to OPPR
        )

        val venter_komplett_søknad = mutableMapOf(
            "7003" to OPPR,
            "9001" to UTFO
        )

        val eventBuilder = eventBuilder(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        val modell = kjørEventer(eventBuilder, legeærklæring, venter_komplett_søknad)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue()

    }

    @Test
    fun `Beholder reservasjon hvis går til vent på OMP`() {
        val legeærklæring = mutableMapOf(
            "9001" to OPPR
        )

        val venter_komplett_søknad = mutableMapOf(
            "7003" to OPPR,
            "9001" to UTFO
        )


        val eventBuilder = eventBuilder(FagsakYtelseType.OMSORGSPENGER)

        val modell = kjørEventer(eventBuilder, legeærklæring, venter_komplett_søknad)
        assertThat(modell.fikkEndretAksjonspunkt()).isFalse()

    }

    @Test
    fun `Fjerner reservasjon hvis beslutter har gjort seg ferdig`() {
        fjernReservasjonBeslutterFerdig(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        fjernReservasjonBeslutterFerdig(FagsakYtelseType.OMSORGSPENGER)
    }

    private fun fjernReservasjonBeslutterFerdig(fagsakYtelseType: FagsakYtelseType) {
        val tilBeslutter = mutableMapOf(
            "5015" to UTFO,
            "5016" to OPPR,
        )
        val beslutterUtført = mutableMapOf(
            "5015" to UTFO,
            "5016" to UTFO,
        )


        val eventBuilder = eventBuilder(fagsakYtelseType)
        val modell = kjørEventer(eventBuilder, tilBeslutter, beslutterUtført)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue()

    }

    @Test
    fun `Skal fjerne reservasjon hvis alle ap er fullført for andre enn PSB`() {
        val vurder_årskvantum = mutableMapOf(
            "9020" to UTFO,
            "9003" to OPPR
        )
        val vurder_omsorgenfor = mutableMapOf(
            "9020" to UTFO,
            "9003" to UTFO
        )


        val eventBuilder = eventBuilder(FagsakYtelseType.OMSORGSPENGER_KS)
        val modell = kjørEventer(eventBuilder, vurder_årskvantum, vurder_omsorgenfor)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue()

    }

    @Test
    fun `Skal IKKE fjerne reservasjon selv om alle aktive ikke-beslutter aksjonspunkter for PSB er fullført`() {
        val vurder_årskvantum = mutableMapOf(
            "9020" to UTFO,
            "9003" to OPPR
        )
        val vurder_omsorgenfor = mutableMapOf(
            "9020" to UTFO,
            "9003" to UTFO
        )


        val eventBuilder = eventBuilder(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        val modell = kjørEventer(eventBuilder, vurder_årskvantum, vurder_omsorgenfor)
        assertThat(modell.fikkEndretAksjonspunkt()).isFalse()

    }

    @Test
    fun `Skal IKKE fjerne reservasjon hvis duplikat event`() {
        val tilBeslutter = mutableMapOf(
            "5015" to UTFO,
            "5016" to OPPR
        )

        val eventBuilder = eventBuilder(FagsakYtelseType.OMSORGSPENGER_KS)
        val modell = kjørEventer(eventBuilder, tilBeslutter, tilBeslutter)

        assertThat(modell.fikkEndretAksjonspunkt()).isFalse()

    }

    private fun kjørEventer(eventBuilder: (Map<String, String>) -> BehandlingProsessEventDto,
                            vararg ap: Map<String, String>): K9SakModell {

        val eventer = ap.map { eventBuilder(it) }.toMutableList()
        return K9SakModell(eventer)
    }

    //        @Test
    fun `Tolker som endret reservasjon hvis både 5016 og en annen AP er aktiv samtidig`() {
        val eventer = mutableListOf<BehandlingProsessEventDto>()
        val modell = K9SakModell(eventer)
        val psbEventBuilder = eventBuilder(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        val tilBeslutter = psbEventBuilder(
            mutableMapOf(
                "5015" to UTFO,
                "5016" to OPPR,
                "9001" to UTFO,
                "9203" to AVBR
            )
        )

        val foreslåVedtakAvbrutt = psbEventBuilder(
            mutableMapOf(
                "5015" to AVBR,
                "5016" to OPPR,
                "9001" to UTFO,
                "9203" to AVBR
            )
        )

        eventer.add(tilBeslutter)
        eventer.add(foreslåVedtakAvbrutt)
        assertThat(modell.fikkEndretAksjonspunkt()).isFalse()

        val tilbakeHopp9001 = psbEventBuilder(
            mutableMapOf(
                "5015" to AVBR,
                "5016" to OPPR,
                "9001" to OPPR,
                "9203" to AVBR
            )
        )

        eventer.add(tilbakeHopp9001)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue()

        val ferdigstill9001 = psbEventBuilder(
            mutableMapOf(
                "5015" to AVBR,
                "5016" to OPPR,
                "9001" to UTFO,
                "9203" to AVBR
            )
        )

        eventer.add(ferdigstill9001)
        assertThat(modell.fikkEndretAksjonspunkt()).isFalse()

        val foreslåVedtakPåNytt = psbEventBuilder(
            mutableMapOf(
                "5015" to OPPR,
                "5016" to OPPR,
                "9001" to UTFO,
                "9203" to AVBR
            )
        )

        eventer.add(foreslåVedtakPåNytt)
        assertThat(modell.fikkEndretAksjonspunkt()).isFalse()


        val tilBeslutterPåNytt = psbEventBuilder(
            mutableMapOf(
                "5015" to UTFO,
                "5016" to OPPR,
                "9001" to UTFO,
                "9203" to AVBR
            )
        )

        eventer.add(tilBeslutterPåNytt)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue()


        val beslutterUtført = psbEventBuilder(
            mutableMapOf(
                "5015" to UTFO,
                "5016" to UTFO,
                "9001" to UTFO,
                "9203" to AVBR
            )
        )

        eventer.add(beslutterUtført)
        assertThat(modell.fikkEndretAksjonspunkt()).isTrue() // usikker

    }

    private fun eventBuilder(fagsakYtelseType: FagsakYtelseType) = {
            aksjonspunkter: Map<String, String> ->
        BehandlingProsessEventDto(
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
            ytelseTypeKode = fagsakYtelseType.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = aksjonspunkter.toMutableMap(),
            aksjonspunktTilstander = aksjonspunkter.tilAksjonspunktTilstandDtoer()
        )
    }

    private fun Map<String, String>.tilAksjonspunktTilstandDtoer() =
        this.entries.map { AksjonspunktTilstandDto(it.key, AksjonspunktStatus.fraKode(it.value), null, null, null)}
}

