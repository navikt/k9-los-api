package no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.mapper

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.kodeverk.UpyOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.upy.oppgavedefinisjon.UngdomsprogramytelsenFeltIder
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.VaskOppgaveversjon
import no.nav.ung.kodeverk.Fagsystem
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val MANUELT_AP = AksjonspunktDefinisjon.KONTROLLER_INNTEKT
private val FATTER_VEDTAK_AP = AksjonspunktDefinisjon.FATTER_VEDTAK
private val AUTOPUNKT_AP = AksjonspunktDefinisjon.AUTO_SATT_PÅ_VENT_RAPPORTERINGSFRIST
private val EVENT_TID = LocalDateTime.of(2026, 3, 4, 10, 0)
private val OPPRETTET_BEHANDLING = LocalDateTime.of(2026, 2, 1, 8, 30, 15)

private fun ap(
    aksjonspunkt: AksjonspunktDefinisjon,
    status: AksjonspunktStatus = AksjonspunktStatus.OPPRETTET,
    venteårsak: Venteårsak = Venteårsak.UDEFINERT,
    fristTid: LocalDateTime? = null,
) = AksjonspunktTilstandDto(
    aksjonspunkt.kode,
    status,
    venteårsak,
    null,
    fristTid,
    OPPRETTET_BEHANDLING,
    OPPRETTET_BEHANDLING,
)

private fun lagEvent(
    aksjonspunktTilstander: List<AksjonspunktTilstandDto> = emptyList(),
    behandlingTypeKode: String = BehandlingType.FØRSTEGANGSSØKNAD.kode,
    behandlingStatus: String = BehandlingStatus.UTREDES.kode,
    behandlingSteg: String? = null,
    ytelseTypeKode: String = FagsakYtelseType.UNGDOMSYTELSE.kode,
    eventHendelse: EventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
    eldsteDatoMedEndringFraSøker: LocalDateTime? = null,
    vedtaksdato: LocalDate? = null,
    nyeKrav: Boolean? = null,
    behandlingsårsaker: List<String> = emptyList(),
    eventTid: LocalDateTime = EVENT_TID,
) = UngSakEventDto(
    eksternId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
    fagsystem = Fagsystem.UNG_SAK,
    saksnummer = "UPY123456",
    aktørId = "1234567890123",
    eventTid = eventTid,
    eventHendelse = eventHendelse,
    behandlingStatus = behandlingStatus,
    behandlingSteg = behandlingSteg,
    behandlendeEnhet = "4416",
    ansvarligBeslutterForTotrinn = "BESLUTTER",
    ansvarligSaksbehandlerForTotrinn = "SAKSBEHANDLER",
    navKontorAnsvarligSaksbehandler = null,
    navKontorBeslutter = null,
    resultatType = null,
    ytelseTypeKode = ytelseTypeKode,
    behandlingTypeKode = behandlingTypeKode,
    eldsteDatoMedEndringFraSøker = eldsteDatoMedEndringFraSøker,
    opprettetBehandling = OPPRETTET_BEHANDLING,
    aksjonspunktTilstander = aksjonspunktTilstander,
    nyeKrav = nyeKrav,
    vedtaksdato = vedtaksdato,
    behandlingsårsaker = behandlingsårsaker,
)

private fun lagEventLagret(event: UngSakEventDto) = EventLagret.UngSak(
    nøkkelId = 1L,
    eksternId = event.eksternId.toString(),
    eksternVersjon = event.eventTid.toString(),
    eventJson = LosObjectMapper.instance.writeValueAsString(event),
    opprettet = event.eventTid,
    dirty = false,
)

private fun forrigeOppgave(vararg verdier: Pair<String, String?>): OppgaveV3 {
    val verdiPerFelt = verdier.toMap()
    val oppgave = mockk<OppgaveV3>()
    every { oppgave.hentVerdi(any()) } answers { verdiPerFelt[firstArg<String>()] }
    return oppgave
}

private fun List<OppgaveFeltverdiDto>.verdierFor(nøkkel: String): List<String?> =
    filter { it.nøkkel == nøkkel }.map { it.verdi }

private fun List<OppgaveFeltverdiDto>.verdiFor(nøkkel: String): String? =
    single { it.nøkkel == nøkkel }.verdi

private fun feltverdier(event: UngSakEventDto, forrige: OppgaveV3? = null): List<OppgaveFeltverdiDto> =
    SakEventTilOppgaveMapper.lagOppgaveDto(lagEventLagret(event), forrige, 1).dto.feltverdier

class SakEventTilOppgaveMapperTest {

    @Test
    fun `kaster når ytelsen ikke er ungdomsprogramytelsen`() {
        val eventLagret = lagEventLagret(lagEvent(ytelseTypeKode = FagsakYtelseType.AKTIVITETSPENGER.kode))

        assertThrows(IllegalArgumentException::class.java) {
            SakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, null, 1)
        }
    }

    @Test
    fun `vanlig event gir ny oppgaveversjon`() {
        val innsending = SakEventTilOppgaveMapper.lagOppgaveDto(lagEventLagret(lagEvent()), null, 7)

        assertThat(innsending).isInstanceOf(NyOppgaveversjon::class)
    }

    @Test
    fun `vaskeevent gir vaskeoppgaveversjon`() {
        val eventLagret = lagEventLagret(lagEvent(eventHendelse = EventHendelse.VASKEEVENT))

        val innsending = SakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, null, 7)

        assertThat(innsending).isInstanceOf(VaskOppgaveversjon::class)
    }

    @Test
    fun `førstegangssøknad og revurdering gir ordinær oppgave`() {
        listOf(BehandlingType.FØRSTEGANGSSØKNAD, BehandlingType.REVURDERING).forEach { behandlingType ->
            assertThat(SakEventTilOppgaveMapper.utledOppgavetype(lagEvent(behandlingTypeKode = behandlingType.kode)))
                .isEqualTo(UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR)
        }
    }

    @Test
    fun `klage og anke gir klageoppgave`() {
        listOf(BehandlingType.KLAGE, BehandlingType.ANKE).forEach { behandlingType ->
            assertThat(SakEventTilOppgaveMapper.utledOppgavetype(lagEvent(behandlingTypeKode = behandlingType.kode)))
                .isEqualTo(UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE)
        }
    }

    @Test
    fun `tilbakekreving og udefinert behandlingstype er ugyldig`() {
        listOf(
            BehandlingType.TILBAKEKREVING,
            BehandlingType.REVURDERING_TILBAKEKREVING,
            BehandlingType.UDEFINERT,
        ).forEach { behandlingType ->
            assertThrows(IllegalStateException::class.java) {
                SakEventTilOppgaveMapper.utledOppgavetype(lagEvent(behandlingTypeKode = behandlingType.kode))
            }
        }
    }

    @Test
    fun `behandlingsstatus og åpne aksjonspunkt bestemmer oppgavestatus`() {
        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(lagEvent(
            behandlingStatus = BehandlingStatus.OPPRETTET.kode,
            aksjonspunktTilstander = listOf(ap(MANUELT_AP)),
        ))).isEqualTo(Oppgavestatus.UAVKLART)

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(lagEvent(
            behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
        ))).isEqualTo(Oppgavestatus.LUKKET)

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(lagEvent(
            aksjonspunktTilstander = listOf(ap(MANUELT_AP)),
        ))).isEqualTo(Oppgavestatus.AAPEN)

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(lagEvent(
            aksjonspunktTilstander = listOf(ap(MANUELT_AP), ap(AUTOPUNKT_AP)),
        ))).isEqualTo(Oppgavestatus.VENTER)
    }

    @Test
    fun `reservasjonsnøkkel skiller vanlig oppgave fra beslutteroppgave`() {
        val vanlig = lagEventLagret(lagEvent())
        val hosBeslutter = lagEventLagret(lagEvent(aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP))))

        assertThat(SakEventTilOppgaveMapper.utledReservasjonsnokkel(vanlig, false))
            .isEqualTo("UPY_b_del1_1234567890123")
        assertThat(SakEventTilOppgaveMapper.utledReservasjonsnokkel(hosBeslutter, true))
            .isEqualTo("UPY_b_del1_1234567890123_beslutter")
    }

    @Test
    fun `mapper sentrale UPY-feltverdier og oppgaveverdier`() {
        val event = lagEvent(nyeKrav = true, behandlingsårsaker = listOf("RE-ANNET", "RE-KLAG"))
        val innsending = SakEventTilOppgaveMapper.lagOppgaveDto(lagEventLagret(event), null, 1)
        val dto = innsending.dto

        assertThat(dto.eksternId).isEqualTo(event.eksternId.toString())
        assertThat(dto.eksternVersjon).isEqualTo(EVENT_TID.toString())
        assertThat(dto.type).isEqualTo(UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR)
        assertThat(dto.status).isEqualTo(Oppgavestatus.UAVKLART)
        assertThat(dto.reservasjonsnøkkel).isEqualTo("UPY_b_del1_1234567890123")
        assertThat(dto.feltverdier.verdiFor(UngdomsprogramytelsenFeltIder.Sak.AKTOR_ID)).isEqualTo("1234567890123")
        assertThat(dto.feltverdier.verdiFor(UngdomsprogramytelsenFeltIder.Sak.FAGSYSTEM)).isEqualTo(Fagsystem.UNG_SAK.kode)
        assertThat(dto.feltverdier.verdiFor(UngdomsprogramytelsenFeltIder.Sak.SAKSNUMMER)).isEqualTo("UPY123456")
        assertThat(dto.feltverdier.verdiFor(UngdomsprogramytelsenFeltIder.Behandling.RESULTATTYPE))
            .isEqualTo(BehandlingResultatType.IKKE_FASTSATT.kode)
        assertThat(dto.feltverdier.verdiFor(UngdomsprogramytelsenFeltIder.Vedtak.YTELSESTYPE))
            .isEqualTo(FagsakYtelseType.UNGDOMSYTELSE.kode)
        assertThat(dto.feltverdier.verdiFor(UngdomsprogramytelsenFeltIder.Soknad.NYE_KRAV)).isEqualTo("true")
        assertThat(dto.feltverdier.verdierFor(UngdomsprogramytelsenFeltIder.Behandling.ARSAK))
            .containsExactlyInAnyOrder("RE-ANNET", "RE-KLAG")
    }

    @Test
    fun `aksjonspunktverdier fordeles etter status og steg`() {
        val event = lagEvent(
            behandlingSteg = MANUELT_AP.behandlingSteg.kode,
            aksjonspunktTilstander = listOf(
                ap(MANUELT_AP),
                ap(FATTER_VEDTAK_AP),
                ap(AUTOPUNKT_AP, AksjonspunktStatus.UTFØRT),
            ),
        )

        val verdier = feltverdier(event)

        assertThat(verdier.verdierFor(UngdomsprogramytelsenFeltIder.Aksjonspunkt.ALLE))
            .containsExactlyInAnyOrder(MANUELT_AP.kode, FATTER_VEDTAK_AP.kode, AUTOPUNKT_AP.kode)
        assertThat(verdier.verdierFor(UngdomsprogramytelsenFeltIder.Aksjonspunkt.AKTIVE))
            .containsExactlyInAnyOrder(MANUELT_AP.kode, FATTER_VEDTAK_AP.kode)
        assertThat(verdier.verdierFor(UngdomsprogramytelsenFeltIder.Aksjonspunkt.LOSBART))
            .containsOnly(MANUELT_AP.kode)
        assertThat(verdier.verdierFor(UngdomsprogramytelsenFeltIder.Aksjonspunkt.UTFORT))
            .containsOnly(AUTOPUNKT_AP.kode)
    }

    @Test
    fun `besluttertid og datoer bevares fra tidligere oppgave`() {
        val beslutterTid = "2026-01-01T09:00"
        val mottattDato = "2026-01-05T07:00"
        val registrertDato = "2025-12-24T10:00"
        val vedtaksdato = "2026-02-28"
        val event = lagEvent(aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP)))
        val verdier = feltverdier(
            event,
            forrigeOppgave(
                UngdomsprogramytelsenFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER to beslutterTid,
                UngdomsprogramytelsenFeltIder.Sak.MOTTATT_DATO to mottattDato,
                UngdomsprogramytelsenFeltIder.Sak.REGISTRERT_DATO to registrertDato,
                UngdomsprogramytelsenFeltIder.Vedtak.DATO to vedtaksdato,
            ),
        )

        assertThat(verdier.verdiFor(UngdomsprogramytelsenFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER))
            .isEqualTo(beslutterTid)
        assertThat(verdier.verdiFor(UngdomsprogramytelsenFeltIder.Sak.MOTTATT_DATO)).isEqualTo(mottattDato)
        assertThat(verdier.verdiFor(UngdomsprogramytelsenFeltIder.Sak.REGISTRERT_DATO)).isEqualTo(registrertDato)
        assertThat(verdier.verdiFor(UngdomsprogramytelsenFeltIder.Vedtak.DATO)).isEqualTo(vedtaksdato)
    }

    @Test
    fun `ventende aksjonspunkt gir venteårsak og frist`() {
        val frist = LocalDateTime.of(2026, 3, 18, 12, 0)
        val verdier = feltverdier(
            lagEvent(
                aksjonspunktTilstander = listOf(
                    ap(AUTOPUNKT_AP, venteårsak = Venteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST, fristTid = frist)
                )
            )
        )

        assertThat(verdier.verdiFor(UngdomsprogramytelsenFeltIder.Ventetid.AKTIV_ARSAK))
            .isEqualTo(Venteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST.kode)
        assertThat(verdier.verdiFor(UngdomsprogramytelsenFeltIder.Ventetid.AKTIV_FRIST)).isEqualTo(frist.toString())
    }
}
