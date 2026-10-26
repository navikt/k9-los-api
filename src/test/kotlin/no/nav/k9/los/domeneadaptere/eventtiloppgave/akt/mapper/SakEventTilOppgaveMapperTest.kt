package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.VaskOppgaveversjon
import no.nav.ung.kodeverk.Fagsystem
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType
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
import java.time.temporal.ChronoUnit
import java.util.UUID

private val NAV_KONTOR_AP = AksjonspunktDefinisjon.NAV_KONTOR_FORESLÅR_VILKÅR
private val NAV_SENTRALT_AP = AksjonspunktDefinisjon.KONTROLLER_INNTEKT
private val FORESLÅ_VEDTAK_AP = AksjonspunktDefinisjon.FORESLÅ_VEDTAK
private val FATTER_VEDTAK_AP = AksjonspunktDefinisjon.FATTER_VEDTAK
private val AUTOPUNKT_AP = AksjonspunktDefinisjon.AUTO_SATT_PÅ_VENT_RAPPORTERINGSFRIST

private val EVENT_TID = LocalDateTime.of(2026, 3, 4, 10, 0, 0)
private val OPPRETTET_BEHANDLING = LocalDateTime.of(2026, 2, 1, 8, 30, 15, 123_456_789)

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
    ytelseTypeKode: String = FagsakYtelseType.AKTIVITETSPENGER.kode,
    eventHendelse: EventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
    eldsteDatoMedEndringFraSøker: LocalDateTime? = null,
    vedtaksdato: LocalDate? = null,
    nyeKrav: Boolean? = null,
    behandlingsårsaker: List<String> = emptyList(),
    navKontorAnsvarligSaksbehandler: String? = "LOKAL_SAKSBEHANDLER",
    navKontorBeslutter: String? = "LOKAL_BESLUTTER",
    ansvarligSaksbehandlerForTotrinn: String? = "SENTRAL_SAKSBEHANDLER",
    ansvarligBeslutterForTotrinn: String? = "SENTRAL_BESLUTTER",
    eventTid: LocalDateTime = EVENT_TID,
) = UngSakEventDto(
    eksternId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
    fagsystem = Fagsystem.UNG_SAK,
    saksnummer = "AKT123456",
    aktørId = "1234567890123",
    eventTid = eventTid,
    eventHendelse = eventHendelse,
    behandlingStatus = behandlingStatus,
    behandlingSteg = behandlingSteg,
    behandlendeEnhet = "4416",
    ansvarligBeslutterForTotrinn = ansvarligBeslutterForTotrinn,
    ansvarligSaksbehandlerForTotrinn = ansvarligSaksbehandlerForTotrinn,
    navKontorAnsvarligSaksbehandler = navKontorAnsvarligSaksbehandler,
    navKontorBeslutter = navKontorBeslutter,
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

    // --- lagOppgaveDto: guards og innsendingstype ---

    @Test
    fun `kaster når ytelsen ikke er aktivitetspenger`() {
        val eventLagret = lagEventLagret(lagEvent(ytelseTypeKode = FagsakYtelseType.UNGDOMSYTELSE.kode))

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

    // --- utledOppgavetype ---

    @Test
    fun `åpent lokalkontoraksjonspunkt gir del1`() {
        val event = lagEvent(aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP)))

        assertThat(SakEventTilOppgaveMapper.utledOppgavetype(event))
            .isEqualTo(AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1)
    }

    @Test
    fun `lokalkontoraksjonspunkt som er utført gir del2`() {
        val event = lagEvent(
            aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP, AksjonspunktStatus.UTFØRT))
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavetype(event))
            .isEqualTo(AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2)
    }

    @Test
    fun `kun nav-sentralt aksjonspunkt gir del2`() {
        val event = lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP)))

        assertThat(SakEventTilOppgaveMapper.utledOppgavetype(event))
            .isEqualTo(AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2)
    }

    @Test
    fun `revurdering følger samme del-utledning som førstegangssøknad`() {
        val event = lagEvent(
            behandlingTypeKode = BehandlingType.REVURDERING.kode,
            aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavetype(event))
            .isEqualTo(AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1)
    }

    @Test
    fun `klage og anke gir klageoppgave`() {
        listOf(BehandlingType.KLAGE, BehandlingType.ANKE).forEach { behandlingType ->
            val event = lagEvent(behandlingTypeKode = behandlingType.kode)

            assertThat(SakEventTilOppgaveMapper.utledOppgavetype(event))
                .isEqualTo(AktOppgavetypenavn.AKTIVITETSPENGERKLAGE)
        }
    }

    @Test
    fun `tilbakekreving og udefinert behandlingstype er ugyldig`() {
        listOf(
            BehandlingType.TILBAKEKREVING,
            BehandlingType.REVURDERING_TILBAKEKREVING,
            BehandlingType.UDEFINERT,
        ).forEach { behandlingType ->
            val event = lagEvent(behandlingTypeKode = behandlingType.kode)

            assertThrows(IllegalStateException::class.java) {
                SakEventTilOppgaveMapper.utledOppgavetype(event)
            }
        }
    }

    // --- utledOppgavestatus ---

    @Test
    fun `behandling under opprettelse er uavklart`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.OPPRETTET.kode,
            aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.UAVKLART)
    }

    @Test
    fun `avsluttet behandling er lukket`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
            aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.LUKKET)
    }

    @Test
    fun `åpent manuelt aksjonspunkt gir aapen`() {
        val event = lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP)))

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `åpent autopunkt gir venter`() {
        val event = lagEvent(aksjonspunktTilstander = listOf(ap(AUTOPUNKT_AP)))

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.VENTER)
    }

    @Test
    fun `åpent autopunkt vinner over åpent manuelt aksjonspunkt`() {
        val event = lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP), ap(AUTOPUNKT_AP)))

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.VENTER)
    }

    @Test
    fun `utredes uten åpne aksjonspunkt er uavklart`() {
        val event = lagEvent(
            aksjonspunktTilstander = listOf(
                ap(NAV_SENTRALT_AP, AksjonspunktStatus.UTFØRT),
                ap(AUTOPUNKT_AP, AksjonspunktStatus.AVBRUTT),
            )
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.UAVKLART)
    }

    @Test
    fun `fatter vedtak med åpent manuelt aksjonspunkt er aapen`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.FATTER_VEDTAK.kode,
            aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `nav-kontor beslutter vilkår med åpent manuelt aksjonspunkt er aapen`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode,
            aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `nav-kontor beslutter vilkår med åpent autopunkt venter`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode,
            aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP), ap(AUTOPUNKT_AP)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.VENTER)
    }

    @Test
    fun `nav-kontor beslutter vilkår uten åpne aksjonspunkt er uavklart`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode,
            aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP, AksjonspunktStatus.UTFØRT)),
        )

        assertThat(SakEventTilOppgaveMapper.utledOppgavestatus(event)).isEqualTo(Oppgavestatus.UAVKLART)
    }

    @Test
    fun `lagrede eventer med gamle lokalkontor-koder leses som nav-kontor-koder`() {
        val event = lagEvent(
            behandlingStatus = BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode,
            behandlingSteg = BehandlingStegType.NAV_KONTOR_FORESLÅ_VILKÅR.kode,
            aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP)),
        )
        val gammelJson = LosObjectMapper.instance.writeValueAsString(event)
            .replace(BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode, "LOKALKONTOR_BESLUTTER_VILKÅR")
            .replace(BehandlingStegType.NAV_KONTOR_FORESLÅ_VILKÅR.kode, "LOKALKONTOR_FORESLÅ_VILKÅR_STEG")

        val eventLagret = lagEventLagret(event).copy(eventJson = gammelJson)

        assertThat(eventLagret.eventDto.behandlingStatus).isEqualTo(BehandlingStatus.NAV_KONTOR_BESLUTTER_VILKÅR.kode)
        assertThat(eventLagret.eventDto.behandlingSteg).isEqualTo(BehandlingStegType.NAV_KONTOR_FORESLÅ_VILKÅR.kode)
        val feltverdier = SakEventTilOppgaveMapper.lagOppgaveDto(eventLagret, null, 1).dto.feltverdier
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly(NAV_KONTOR_AP.kode)
    }

    // --- utledReservasjonsnokkel ---

    @Test
    fun `reservasjonsnøkkel for del1`() {
        val eventLagret = lagEventLagret(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP))))

        assertThat(SakEventTilOppgaveMapper.utledReservasjonsnokkel(eventLagret, false))
            .isEqualTo("AKT_b_del1_1234567890123")
    }

    @Test
    fun `reservasjonsnøkkel for del2`() {
        val eventLagret = lagEventLagret(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP))))

        assertThat(SakEventTilOppgaveMapper.utledReservasjonsnokkel(eventLagret, false))
            .isEqualTo("AKT_b_del2_1234567890123")
    }

    @Test
    fun `reservasjonsnøkkel hos beslutter får eget suffiks`() {
        val eventLagret = lagEventLagret(lagEvent(aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP))))

        assertThat(SakEventTilOppgaveMapper.utledReservasjonsnokkel(eventLagret, true))
            .isEqualTo("AKT_b_del2_1234567890123_beslutter")
    }

    @Test
    fun `reservasjonsnøkkel for klage er ikke implementert`() {
        val eventLagret = lagEventLagret(lagEvent(behandlingTypeKode = BehandlingType.KLAGE.kode))

        assertThrows(NotImplementedError::class.java) {
            SakEventTilOppgaveMapper.utledReservasjonsnokkel(eventLagret, false)
        }
    }

    @Test
    fun `reservasjonsnøkkel brukes i oppgavedto og beslutter utledes fra aksjonspunkt`() {
        val dto = SakEventTilOppgaveMapper.lagOppgaveDto(
            lagEventLagret(lagEvent(aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP)))),
            null,
            1,
        ).dto

        assertThat(dto.reservasjonsnøkkel).isEqualTo("AKT_b_del2_1234567890123_beslutter")
        assertThat(dto.feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.LIGGER_HOS_BESLUTTER.id))
            .isEqualTo("true")
    }

    @Test
    fun `avbrutt fatter vedtak betyr ikke hos beslutter`() {
        val dto = SakEventTilOppgaveMapper.lagOppgaveDto(
            lagEventLagret(
                lagEvent(aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP, AksjonspunktStatus.AVBRUTT)))
            ),
            null,
            1,
        ).dto

        assertThat(dto.feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.LIGGER_HOS_BESLUTTER.id))
            .isEqualTo("false")
    }

    // --- tidFørsteGangHosBeslutter ---

    @Test
    fun `tid første gang hos beslutter settes når oppgaven havner hos beslutter`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(FATTER_VEDTAK_AP))))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER.id))
            .isEqualTo(EVENT_TID.toString())
    }

    @Test
    fun `tid første gang hos beslutter beholdes fra forrige oppgave`() {
        val tidligere = "2026-01-01T09:00"
        val feltverdier = feltverdier(
            event = lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP))),
            forrige = forrigeOppgave(
                AktivitetspengerFelter.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER.id to tidligere
            ),
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER.id))
            .isEqualTo(tidligere)
    }

    @Test
    fun `tid første gang hos beslutter settes ikke når oppgaven aldri har vært hos beslutter`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP))))

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER.id))
            .isEqualTo(emptyList<String?>())
    }

    // --- datoutledning ---

    @Test
    fun `mottatt dato hentes fra eldste endring fra søker og trunkeres til sekund`() {
        val eldsteEndring = LocalDateTime.of(2026, 2, 20, 12, 0, 0, 999_000_000)
        val feltverdier = feltverdier(lagEvent(eldsteDatoMedEndringFraSøker = eldsteEndring))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.MOTTATT_DATO.id))
            .isEqualTo(eldsteEndring.truncatedTo(ChronoUnit.SECONDS).toString())
    }

    @Test
    fun `mottatt dato arves fra forrige oppgave når eventet mangler endring fra søker`() {
        val feltverdier = feltverdier(
            event = lagEvent(),
            forrige = forrigeOppgave(AktivitetspengerFelter.Sak.MOTTATT_DATO.id to "2026-01-05T07:00"),
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.MOTTATT_DATO.id)).isEqualTo("2026-01-05T07:00")
    }

    @Test
    fun `mottatt dato faller tilbake på registrert dato fra forrige oppgave`() {
        val feltverdier = feltverdier(
            event = lagEvent(),
            forrige = forrigeOppgave(AktivitetspengerFelter.Sak.REGISTRERT_DATO.id to "2026-01-06T07:00"),
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.MOTTATT_DATO.id)).isEqualTo("2026-01-06T07:00")
    }

    @Test
    fun `mottatt og registrert dato faller tilbake på opprettet behandling`() {
        val feltverdier = feltverdier(lagEvent())
        val forventet = OPPRETTET_BEHANDLING.truncatedTo(ChronoUnit.SECONDS).toString()

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.MOTTATT_DATO.id)).isEqualTo(forventet)
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.REGISTRERT_DATO.id)).isEqualTo(forventet)
    }

    @Test
    fun `registrert dato er uendret fra forrige oppgave`() {
        val feltverdier = feltverdier(
            event = lagEvent(),
            forrige = forrigeOppgave(AktivitetspengerFelter.Sak.REGISTRERT_DATO.id to "2025-12-24T10:00"),
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.REGISTRERT_DATO.id)).isEqualTo("2025-12-24T10:00")
    }

    @Test
    fun `vedtaksdato hentes fra eventet og ellers fra forrige oppgave`() {
        val medVedtaksdato = feltverdier(lagEvent(vedtaksdato = LocalDate.of(2026, 3, 1)))
        assertThat(medVedtaksdato.verdiFor(AktivitetspengerFelter.Vedtak.DATO.id)).isEqualTo("2026-03-01")

        val arvet = feltverdier(
            event = lagEvent(),
            forrige = forrigeOppgave(AktivitetspengerFelter.Vedtak.DATO.id to "2026-02-28"),
        )
        assertThat(arvet.verdiFor(AktivitetspengerFelter.Vedtak.DATO.id)).isEqualTo("2026-02-28")

        val uten = feltverdier(lagEvent())
        assertThat(uten.verdiFor(AktivitetspengerFelter.Vedtak.DATO.id)).isEqualTo(null)
    }

    // --- ansvarlig saksbehandler og beslutter ---

    @Test
    fun `del1 bruker navkontorfeltene for saksbehandler og beslutter`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_KONTOR_AP))))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_SAKSBEHANDLER.id))
            .isEqualTo("LOKAL_SAKSBEHANDLER")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_BESLUTTER.id))
            .isEqualTo("LOKAL_BESLUTTER")
    }

    @Test
    fun `del2 bruker totrinnsfeltene for saksbehandler og beslutter`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP))))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_SAKSBEHANDLER.id))
            .isEqualTo("SENTRAL_SAKSBEHANDLER")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_BESLUTTER.id))
            .isEqualTo("SENTRAL_BESLUTTER")
    }

    // --- enkeltverdier med defaults ---

    @Test
    fun `resultattype defaulter til ikke fastsatt`() {
        val feltverdier = feltverdier(lagEvent())

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.RESULTATTYPE.id))
            .isEqualTo(BehandlingResultatType.IKKE_FASTSATT.kode)
    }

    @Test
    fun `nye krav mappes kun når det er satt`() {
        assertThat(feltverdier(lagEvent(nyeKrav = true)).verdiFor(AktivitetspengerFelter.Soknad.NYE_KRAV.id))
            .isEqualTo("true")
        assertThat(feltverdier(lagEvent()).verdierFor(AktivitetspengerFelter.Soknad.NYE_KRAV.id))
            .isEqualTo(emptyList<String?>())
    }

    // --- behandlingsårsaker ---

    @Test
    fun `behandlingsårsaker mappes som liste`() {
        val feltverdier = feltverdier(lagEvent(behandlingsårsaker = listOf("RE-ANNET", "RE-KLAG")))

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Behandling.ARSAK.id))
            .containsExactlyInAnyOrder("RE-ANNET", "RE-KLAG")
    }

    @Test
    fun `tomme behandlingsårsaker gir nullverdi`() {
        val feltverdier = feltverdier(lagEvent())

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Behandling.ARSAK.id)).containsOnly(null)
    }

    // --- aksjonspunktfelter ---

    @Test
    fun `aksjonspunkt fordeles på alle, aktive, løsbart, fremtidig, utført og avbrutt`() {
        val event = lagEvent(
            behandlingSteg = NAV_SENTRALT_AP.behandlingSteg.kode,
            aksjonspunktTilstander = listOf(
                ap(NAV_SENTRALT_AP),
                ap(FATTER_VEDTAK_AP),
                ap(FORESLÅ_VEDTAK_AP, AksjonspunktStatus.UTFØRT),
                ap(NAV_KONTOR_AP, AksjonspunktStatus.AVBRUTT),
            ),
        )

        val feltverdier = feltverdier(event)

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id))
            .containsExactlyInAnyOrder(
                NAV_SENTRALT_AP.kode,
                FATTER_VEDTAK_AP.kode,
                FORESLÅ_VEDTAK_AP.kode,
                NAV_KONTOR_AP.kode,
            )
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id))
            .containsExactlyInAnyOrder(NAV_SENTRALT_AP.kode, FATTER_VEDTAK_AP.kode)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id))
            .containsOnly(NAV_SENTRALT_AP.kode)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id))
            .containsOnly(FATTER_VEDTAK_AP.kode)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id))
            .containsOnly(FORESLÅ_VEDTAK_AP.kode)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AVBRUTT.id))
            .containsOnly(NAV_KONTOR_AP.kode)
    }

    @Test
    fun `uten aksjonspunkt settes nullverdier`() {
        val feltverdier = feltverdier(lagEvent())

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AVBRUTT.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id))
            .isEqualTo(emptyList<String?>())
    }

    @Test
    fun `autopunkt er verken løsbart eller fremtidig`() {
        val event = lagEvent(
            behandlingSteg = AUTOPUNKT_AP.behandlingSteg.kode,
            aksjonspunktTilstander = listOf(ap(AUTOPUNKT_AP, venteårsak = Venteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST)),
        )

        val feltverdier = feltverdier(event)

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id))
            .containsOnly(AUTOPUNKT_AP.kode)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
    }

    @Test
    fun `uten behandlingssteg finnes ingen løsbare aksjonspunkt`() {
        val event = lagEvent(behandlingSteg = null, aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP)))

        val feltverdier = feltverdier(event)

        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id))
            .containsOnly(NAV_SENTRALT_AP.kode)
    }

    // --- totrinnskontroll ---

    @Test
    fun `foreslå vedtak markerer totrinnskontroll`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(FORESLÅ_VEDTAK_AP))))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.TOTRINNSKONTROLL.id)).isEqualTo("true")
    }

    @Test
    fun `utført foreslå vedtak markerer fortsatt totrinnskontroll`() {
        val feltverdier = feltverdier(
            lagEvent(aksjonspunktTilstander = listOf(ap(FORESLÅ_VEDTAK_AP, AksjonspunktStatus.UTFØRT)))
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.TOTRINNSKONTROLL.id)).isEqualTo("true")
    }

    @Test
    fun `avbrutt foreslå vedtak gir ikke totrinnskontroll`() {
        val feltverdier = feltverdier(
            lagEvent(aksjonspunktTilstander = listOf(ap(FORESLÅ_VEDTAK_AP, AksjonspunktStatus.AVBRUTT)))
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.TOTRINNSKONTROLL.id)).isEqualTo("false")
    }

    @Test
    fun `uten foreslå vedtak er det ingen totrinnskontroll`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP))))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.TOTRINNSKONTROLL.id)).isEqualTo("false")
    }

    // --- ventetid ---

    @Test
    fun `aktivt autopunkt gir venteårsak og ventefrist`() {
        val frist = LocalDateTime.of(2026, 3, 18, 12, 0)
        val feltverdier = feltverdier(
            lagEvent(
                aksjonspunktTilstander = listOf(
                    ap(AUTOPUNKT_AP, venteårsak = Venteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST, fristTid = frist)
                )
            )
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_ARSAK.id))
            .isEqualTo(Venteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST.kode)
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_FRIST.id)).isEqualTo(frist.toString())
    }

    @Test
    fun `udefinert venteårsak gir ingen ventetid`() {
        val feltverdier = feltverdier(lagEvent(aksjonspunktTilstander = listOf(ap(NAV_SENTRALT_AP))))

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_ARSAK.id)).isEqualTo(null)
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_FRIST.id)).isEqualTo(null)
    }

    @Test
    fun `lukket autopunkt gir ingen ventetid`() {
        val feltverdier = feltverdier(
            lagEvent(
                aksjonspunktTilstander = listOf(
                    ap(
                        AUTOPUNKT_AP,
                        status = AksjonspunktStatus.UTFØRT,
                        venteårsak = Venteårsak.VENT_INNTEKT_RAPPORTERINGSFRIST,
                        fristTid = LocalDateTime.of(2026, 3, 18, 12, 0),
                    )
                )
            )
        )

        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_ARSAK.id)).isEqualTo(null)
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_FRIST.id)).isEqualTo(null)
    }
}