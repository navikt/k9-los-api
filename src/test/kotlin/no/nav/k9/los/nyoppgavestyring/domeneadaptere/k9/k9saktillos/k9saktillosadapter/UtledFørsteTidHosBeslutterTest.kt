package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9saktillos.k9saktillosadapter;

import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktTilstandBuilder
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.EventTilDtoMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UtledFørsteTidHosBeslutterTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `aldri vært hos beslutter gir ingen timestamp for første gang hos beslutter`() {
        val forrigeOppgave = OppgaveTestDataBuilder().lag()
        val event = opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.UTREDES)
        assertNull(EventTilDtoMapper.utledTidFørsteGangHosBeslutter(forrigeOppgave = forrigeOppgave, event) )
    }

    @Test
    fun `første gang hos beslutter skal sette timestamp lik eventtid`() {
        val forrigeOppgave = OppgaveTestDataBuilder().lag()
        val event = hosBeslutter(opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.UTREDES))
        assertEquals(event.eventTid.toString(), EventTilDtoMapper.utledTidFørsteGangHosBeslutter(forrigeOppgave = forrigeOppgave, event)!!.verdi)
    }

    @Test
    fun `tidligere vært hos beslutter skal videreføre beslutter timestamp`() {
        val timestamp = LocalDate.now().minusDays(1).toString()
        val forrigeOppgave = OppgaveTestDataBuilder().medOppgaveFeltVerdi(feltTypeKode = FeltType.TID_FORSTE_GANG_HOS_BESLUTTER, timestamp).lag()
        val event = opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.UTREDES)
        assertEquals(timestamp, EventTilDtoMapper.utledTidFørsteGangHosBeslutter(forrigeOppgave = forrigeOppgave, event)!!.verdi)
    }

    @Test
    fun `ny runde hos beslutter skal bevare opprinnelig timestamp`() {
        val timestamp = LocalDate.now().minusDays(1).toString()
        val forrigeOppgave = OppgaveTestDataBuilder().medOppgaveFeltVerdi(feltTypeKode = FeltType.TID_FORSTE_GANG_HOS_BESLUTTER, timestamp).lag()
        val event = hosBeslutter(opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.UTREDES))
        assertEquals(timestamp, EventTilDtoMapper.utledTidFørsteGangHosBeslutter(forrigeOppgave = forrigeOppgave, event)!!.verdi)
    }

    private fun opprettEvent(fagsakYtelseType: FagsakYtelseType, behandlingStatus: BehandlingStatus) : BehandlingProsessEventDto {
        return BehandlingProsessEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = behandlingStatus.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = fagsakYtelseType.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = emptyMap<String, String>().toMutableMap(),
            aksjonspunktTilstander = emptyList()
        )
    }

    private fun hosBeslutter(eventDto: BehandlingProsessEventDto) : BehandlingProsessEventDto {
        return eventDto.copy(aksjonspunktTilstander = listOf(AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(
            AksjonspunktStatus.OPPRETTET).build()) )
    }
}
