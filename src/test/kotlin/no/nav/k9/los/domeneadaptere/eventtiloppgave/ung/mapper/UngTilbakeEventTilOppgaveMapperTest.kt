package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.mapper

import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerOppgavetyper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.oppgavetypeKode
import no.nav.k9.los.domeneadaptere.eventtiloppgave.tilOppgaveversjon
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon.UngdomsprogramytelsenOppgavetyper
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.VaskOppgaveversjon
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.mapper.UngTilbakeEventTilOppgaveMapper as AvpTilbakeMapper
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class UngTilbakeEventTilOppgaveMapperTest {
    private val tid = LocalDateTime.of(2026, 3, 5, 12, 0)

    private fun event(ytelse: String = FagsakYtelseType.AKTIVITETSPENGER.kode) = UngTilbakeEventDto(
        eksternId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
        fagsystem = "UNGTILBAKE",
        saksnummer = "123456",
        aktørId = "1234567890123",
        eventTid = tid,
        eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
        behandlingStatus = "UTRED",
        behandlingSteg = "VTILBSTEG",
        behandlendeEnhet = "4416",
        ytelseTypeKode = ytelse,
        behandlingTypeKode = BehandlingType.TILBAKEKREVING.kode,
        opprettetBehandling = tid.minusDays(1),
        aksjonspunktKoderMedStatusListe = mapOf(
            "5002" to "OPPR",
            "5003" to "UTFO",
            "5004" to "AVBR",
            "7001" to "UTFO",
            "7003" to "OPPR",
        ),
        førsteFeilutbetaling = LocalDate.of(2025, 1, 2),
        feilutbetaltBeløp = BigDecimal("1234.56"),
    )

    private fun lagret(event: UngTilbakeEventDto): EventLagret.UngTilbake =
        EventLagret.create(1, Fagsystem.UNGTILBAKE, event.eksternId.toString(), event.eventTid.toString(),
            LosObjectMapper.instance.writeValueAsString(event), tid, true) as EventLagret.UngTilbake

    @Test
    fun `mapper begge ytelsene til registrerte oppgavetyper og felt`() {
        for ((ytelse, område) in listOf(
            FagsakYtelseType.AKTIVITETSPENGER.kode to Områder.AKTIVITETSPENGER,
            FagsakYtelseType.UNGDOMSYTELSE.kode to Områder.UNGDOMSPROGRAMYTELSEN,
        )) {
            val lagret = lagret(event(ytelse))
            val oppgave = lagret.tilOppgaveversjon(null, 0).dto
            val definisjon = when (område) {
                Områder.AKTIVITETSPENGER -> AktivitetspengerOppgavetyper.TILBAKE
                Områder.UNGDOMSPROGRAMYTELSEN -> UngdomsprogramytelsenOppgavetyper.TILBAKE
                Områder.K9 -> error("Uventet område")
            }
            assertEquals(område, lagret.område)
            assertEquals(definisjon.id, lagret.oppgavetypeKode().kode)
            assertEquals(definisjon.id, oppgave.type.kode)
            assertEquals(Fagsystem.UNGTILBAKE, lagret.oppgavetypeKode().fagsystem)
            assertEquals(Oppgavestatus.AAPEN, oppgave.status)
            assertTrue(oppgave.feltverdier.all { verdi -> definisjon.felter.any { it.felt.id == verdi.nøkkel } })
            assertTrue(definisjon.felter.filter { it.påkrevd }.all { felt -> oppgave.feltverdier.any { it.nøkkel == felt.felt.id && it.verdi != null } })
            assertTrue(definisjon.felter.none { it.felt.id in setOf("løsbartAksjonspunkt", "fremtidigAksjonspunkt") })
            val felt = oppgave.feltverdier.associate { it.nøkkel to it.verdi }
            assertEquals("1234.56", felt["feilutbetaltBeløp"])
            assertEquals("2025-01-02", felt["førsteFeilutbetalingDato"])
            assertEquals(
                setOf("5002", "7003"),
                oppgave.feltverdier.filter { it.nøkkel == "tilbakeLøsbartAksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
            assertEquals(
                setOf("5003", "7001"),
                oppgave.feltverdier.filter { it.nøkkel == "tilbakeUtførtAksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
            assertEquals(
                setOf("5004"),
                oppgave.feltverdier.filter { it.nøkkel == "tilbakeAvbruttAksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
            assertEquals(
                setOf("TILBAKE_5002", "TILBAKE_5003", "TILBAKE_5004", "TILBAKE_7001", "TILBAKE_7003"),
                oppgave.feltverdier.filter { it.nøkkel == "aksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
            assertEquals(
                setOf("TILBAKE_5002", "TILBAKE_7003"),
                oppgave.feltverdier.filter { it.nøkkel == "aktivtAksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
            assertEquals(
                setOf("TILBAKE_5003", "TILBAKE_7001"),
                oppgave.feltverdier.filter { it.nøkkel == "utførtAksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
            assertEquals(
                setOf("TILBAKE_5004"),
                oppgave.feltverdier.filter { it.nøkkel == "avbruttAksjonspunkt" }.mapNotNull { it.verdi }.toSet(),
            )
        }
        AktivitetspengerOppgavetyper.tilDto("https://example.test")
        UngdomsprogramytelsenOppgavetyper.tilDto()
    }

    @Test
    fun `mapperne avviser eventer fra det andre området`() {
        val avpEvent = lagret(event(FagsakYtelseType.AKTIVITETSPENGER.kode))
        val ungEvent = lagret(event(FagsakYtelseType.UNGDOMSYTELSE.kode))
        assertThrows(IllegalArgumentException::class.java) {
            UngTilbakeEventTilOppgaveMapper.lagOppgaveDto(avpEvent, null, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvpTilbakeMapper.lagOppgaveDto(ungEvent, null, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UngTilbakeEventTilOppgaveMapper.utledReservasjonsnøkkel(avpEvent, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvpTilbakeMapper.utledReservasjonsnøkkel(ungEvent, false)
        }
        val avpOppgave = AvpTilbakeMapper.lagOppgaveDto(avpEvent, null, 0).dto
        val ungOppgave = UngTilbakeEventTilOppgaveMapper.lagOppgaveDto(ungEvent, null, 0).dto
        assertEquals("false", avpOppgave.feltverdier.single { it.nøkkel == "helautomatisk" }.verdi)
        assertEquals("false", ungOppgave.feltverdier.single { it.nøkkel == "helautomatisk" }.verdi)
        assertEquals("AKT_t_1234567890123", avpOppgave.reservasjonsnøkkel)
        assertEquals("UPY_t_1234567890123", ungOppgave.reservasjonsnøkkel)
    }

    @Test
    fun `utleder status, beslutter og reservasjonsnøkkel`() {
        val base = event()
        fun oppgave(status: String, aksjonspunkter: Map<String, String>) =
            lagret(base.copy(behandlingStatus = status, aksjonspunktKoderMedStatusListe = aksjonspunkter)).tilOppgaveversjon(null, 0).dto

        assertEquals(Oppgavestatus.UAVKLART, oppgave("OPPRE", emptyMap()).status)
        assertEquals(Oppgavestatus.LUKKET, oppgave("AVSLU", emptyMap()).status)
        assertEquals(Oppgavestatus.UAVKLART, oppgave("UTRED", emptyMap()).status)
        assertEquals(Oppgavestatus.VENTER, oppgave("UTRED", mapOf("7001" to "OPPR", "5002" to "OPPR")).status)
        val beslutter = oppgave("FVED", mapOf("5005" to "OPPR"))
        assertEquals("AKT_t_1234567890123_beslutter", beslutter.reservasjonsnøkkel)
        assertEquals("true", beslutter.feltverdier.single { it.nøkkel == "liggerHosBeslutter" }.verdi)
        assertEquals(tid.toString(), beslutter.feltverdier.single { it.nøkkel == "tidFørsteGangHosBeslutter" }.verdi)
        assertEquals("AKT_t_1234567890123", oppgave("UTRED", mapOf("5005" to "OPPR", "5004" to "OPPR")).reservasjonsnøkkel)
    }

    @Test
    fun `vask bevarer historiske felt og avviser ukjente koder`() {
        val tidligere = mockk<OppgaveV3>()
        every { tidligere.hentVerdi(any()) } answers {
            when (firstArg<String>()) {
                "feilutbetaltBeløp" -> "10.50"
                "førsteFeilutbetalingDato" -> "2024-01-01"
                "ansvarligSaksbehandler" -> "S123"
                else -> null
            }
        }
        val event = event().copy(eventHendelse = EventHendelse.VASKEEVENT, feilutbetaltBeløp = null, førsteFeilutbetaling = null)
        val resultat = lagret(event).tilOppgaveversjon(tidligere, 3)
        assertEquals(3, assertInstanceOf(VaskOppgaveversjon::class.java, resultat).eventNummer)
        val felt = resultat.dto.feltverdier.associate { it.nøkkel to it.verdi }
        assertEquals("10.50", felt["feilutbetaltBeløp"])
        assertEquals("2024-01-01", felt["førsteFeilutbetalingDato"])
        assertEquals("S123", felt["ansvarligSaksbehandler"])
        assertThrows(IllegalArgumentException::class.java) {
            lagret(event.copy(aksjonspunktKoderMedStatusListe = mapOf("9999" to "OPPR"))).tilOppgaveversjon(null, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            lagret(event.copy(behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode)).tilOppgaveversjon(null, 0)
        }
    }

    @Test
    fun `mapper historiske verdier og tomme aksjonspunkter likt i begge områder`() {
        val tidligere = mockk<OppgaveV3>()
        every { tidligere.hentVerdi(any()) } answers {
            when (firstArg<String>()) {
                "mottattDato" -> "2024-02-03T04:05:06"
                "registrertDato" -> "2024-03-04T05:06:07"
                "ansvarligSaksbehandler" -> "S123"
                "ansvarligBeslutter" -> "B456"
                "tidFørsteGangHosBeslutter" -> "2024-04-05T06:07:08"
                "førsteFeilutbetalingDato" -> "2024-05-06"
                "feilutbetaltBeløp" -> "987.65"
                else -> null
            }
        }

        for ((ytelse, erAktivitetspenger) in listOf(
            FagsakYtelseType.AKTIVITETSPENGER.kode to true,
            FagsakYtelseType.UNGDOMSYTELSE.kode to false,
        )) {
            val event = event(ytelse).copy(
                aksjonspunktKoderMedStatusListe = emptyMap(),
                ansvarligSaksbehandlerIdent = null,
                ansvarligBeslutterIdent = null,
                førsteFeilutbetaling = null,
                feilutbetaltBeløp = null,
            )
            val oppgave = if (erAktivitetspenger) {
                AvpTilbakeMapper.lagOppgaveDto(lagret(event), tidligere, 0).dto
            } else {
                UngTilbakeEventTilOppgaveMapper.lagOppgaveDto(lagret(event), tidligere, 0).dto
            }

            assertEquals(
                listOf(
                    OppgaveFeltverdiDto("behandlingUuid", "11111111-2222-3333-4444-555555555555"),
                    OppgaveFeltverdiDto("behandlingTypekode", BehandlingType.TILBAKEKREVING.kode),
                    OppgaveFeltverdiDto("behandlingsstatus", "UTRED"),
                    OppgaveFeltverdiDto("behandlingssteg", "VTILBSTEG"),
                    OppgaveFeltverdiDto("aktorId", "1234567890123"),
                    OppgaveFeltverdiDto("fagsystem", "UNGTILBAKE"),
                    OppgaveFeltverdiDto("saksnummer", "123456"),
                    OppgaveFeltverdiDto("mottattDato", "2024-02-03T04:05:06"),
                    OppgaveFeltverdiDto("registrertDato", "2024-03-04T05:06:07"),
                    OppgaveFeltverdiDto("ytelsestype", ytelse),
                    OppgaveFeltverdiDto("behandlendeEnhet", "4416"),
                    OppgaveFeltverdiDto("ansvarligSaksbehandler", "S123"),
                    OppgaveFeltverdiDto("ansvarligBeslutter", "B456"),
                    OppgaveFeltverdiDto("liggerHosBeslutter", "false"),
                    OppgaveFeltverdiDto("tidFørsteGangHosBeslutter", "2024-04-05T06:07:08"),
                    OppgaveFeltverdiDto("førsteFeilutbetalingDato", "2024-05-06"),
                    OppgaveFeltverdiDto("feilutbetaltBeløp", "987.65"),
                    OppgaveFeltverdiDto("tilbakeLøsbartAksjonspunkt", null),
                    OppgaveFeltverdiDto("tilbakeUtførtAksjonspunkt", null),
                    OppgaveFeltverdiDto("tilbakeAvbruttAksjonspunkt", null),
                    OppgaveFeltverdiDto("aksjonspunkt", null),
                    OppgaveFeltverdiDto("aktivtAksjonspunkt", null),
                    OppgaveFeltverdiDto("utførtAksjonspunkt", null),
                    OppgaveFeltverdiDto("avbruttAksjonspunkt", null),
                    OppgaveFeltverdiDto("helautomatisk", "true"),
                ),
                oppgave.feltverdier,
            )
        }
    }
}
