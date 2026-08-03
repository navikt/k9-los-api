package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.klagetillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.matchesPredicate
import no.nav.k9.klage.kodeverk.Fagsystem
import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
import no.nav.k9.klage.kodeverk.behandling.BehandlingStegType
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.klage.typer.Periode
import no.nav.k9.los.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private fun verdierFor(feltverdier: List<OppgaveFeltverdiDto>, nøkkel: String): List<String?> =
    feltverdier.filter { it.nøkkel == nøkkel }.map { it.verdi }

private fun lagKlageEvent(
    aksjonspunkttilstander: List<Aksjonspunkttilstand>,
    behandlingSteg: String = BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS.kode,
    behandlingStatus: String = BehandlingStatus.UTREDES.kode
) = K9KlageEventDto(
    eksternId = UUID.randomUUID(),
    fagsystem = Fagsystem.K9SAK,
    eventTid = LocalDateTime.now(),
    opprettetBehandling = LocalDateTime.now(),
    aksjonspunkttilstander = aksjonspunkttilstander,
    påklagdBehandlingType = null,
    påklagdBehandlingId = null,
    utenlandstilsnitt = null,
    behandlingstidFrist = LocalDate.now(),
    saksnummer = "test",
    aktørId = "test",
    eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
    behandlingStatus = behandlingStatus,
    behandlingSteg = behandlingSteg,
    behandlendeEnhet = "test",
    ansvarligBeslutter = "test",
    ansvarligSaksbehandler = "test",
    resultatType = "test",
    ytelseTypeKode = "test",
    behandlingTypeKode = "test",
    fagsakPeriode = Periode(LocalDate.now().minusDays(1), LocalDate.now()),
    pleietrengendeAktørId = AktørId(2L),
    relatertPartAktørId = AktørId(3L),
    vedtaksdato = LocalDate.now(),
    behandlingsårsaker = listOf("test"),
)

private fun aksjonspunkttilstand(kode: String, status: AksjonspunktStatus) =
    Aksjonspunkttilstand(kode, status, null, null, LocalDateTime.now(), LocalDateTime.now())

class EventTilDtoMapperTest {
    @Test
    fun `5016 skal gi til beslutter`() {
        val k9KlageEvent = lagKlageEvent(
            aksjonspunkttilstander = listOf(aksjonspunkttilstand("5016", AksjonspunktStatus.OPPRETTET)),
        )

        val oppgaveDto = KlageEventTilOppgaveMapper.lagOppgaveDto(k9KlageEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true" } }
    }

    @Test
    fun `uten 5016 skal ikke gi til beslutter`() {
        val k9KlageEvent = lagKlageEvent(aksjonspunkttilstander = listOf())

        val oppgaveDto = KlageEventTilOppgaveMapper.lagOppgaveDto(k9KlageEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false" } }
    }

    @Test
    fun `klage med blanding av aksjonspunktstatuser gir riktige aksjonspunktfelt`() {
        val vurderKlageAP = AksjonspunktDefinisjon.MANUELL_VURDERING_AV_KLAGE_NFP
        val fatterVedtakAP = AksjonspunktDefinisjon.FATTER_VEDTAK

        val k9KlageEvent = lagKlageEvent(
            behandlingSteg = vurderKlageAP.behandlingSteg.kode,
            aksjonspunkttilstander = listOf(
                aksjonspunkttilstand(vurderKlageAP.kode, AksjonspunktStatus.OPPRETTET),
                aksjonspunkttilstand(fatterVedtakAP.kode, AksjonspunktStatus.UTFØRT),
            ),
        )

        val oppgaveDto = KlageEventTilOppgaveMapper.lagOppgaveDto(k9KlageEvent, null)
        val feltverdier = oppgaveDto.feltverdier

        // Verdier skal ha KLAGE-prefix
        val prefix = KlageEventTilOppgaveMapper.KLAGE_PREFIX

        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            prefix + fatterVedtakAP.kode
        )
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)

        // vurderKlageAP er OPPRETTET og i nåværende steg → løsbart, ikke fremtidig
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(null)
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            prefix + vurderKlageAP.kode
        )

        // Eksisterende felt
        assertThat(verdierFor(feltverdier, "aksjonspunkt")).containsOnly(
            prefix + vurderKlageAP.kode,
            prefix + fatterVedtakAP.kode
        )
        assertThat(verdierFor(feltverdier, "aktivtAksjonspunkt")).containsOnly(
            prefix + vurderKlageAP.kode
        )
    }

    @Test
    fun `klage hos beslutter med utført og avbrutt aksjonspunkt`() {
        val fatterVedtakAP = AksjonspunktDefinisjon.FATTER_VEDTAK
        val vurderKlageAP = AksjonspunktDefinisjon.MANUELL_VURDERING_AV_KLAGE_NFP

        val k9KlageEvent = lagKlageEvent(
            behandlingSteg = fatterVedtakAP.behandlingSteg.kode,
            behandlingStatus = BehandlingStatus.FATTER_VEDTAK.kode,
            aksjonspunkttilstander = listOf(
                aksjonspunkttilstand(fatterVedtakAP.kode, AksjonspunktStatus.OPPRETTET),
                aksjonspunkttilstand(vurderKlageAP.kode, AksjonspunktStatus.AVBRUTT),
            ),
        )

        val oppgaveDto = KlageEventTilOppgaveMapper.lagOppgaveDto(k9KlageEvent, null)
        val feltverdier = oppgaveDto.feltverdier
        val prefix = KlageEventTilOppgaveMapper.KLAGE_PREFIX

        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(null)
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(
            prefix + vurderKlageAP.kode
        )
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(null)
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            prefix + fatterVedtakAP.kode
        )
    }
}