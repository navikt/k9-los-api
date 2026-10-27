package no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.mapper

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFeltIder
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveDto
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SakEventTilOppgaveMapperBostedEventserieTest {

    private val aktørId = "1234567890123"
    private val eksternId = "788723c0-1643-48f7-8107-700f37d4b3c1"

    private val eventer: List<EventLagret.UngSak> = lesEventer()

    private fun lesEventer(): List<EventLagret.UngSak> {
        val json = javaClass.getResource("/no/nav/k9/los/domeneadaptere/ungsaktillos/aktivitetspengerBostedEventserie.json")!!.readText()
        val noder = LosObjectMapper.instance.readTree(json) as ArrayNode
        return noder.mapIndexed { indeks, node ->
            (node as ObjectNode).put("aktørId", aktørId)
            val eventTid = LocalDateTime.parse(node.get("eventTid").asText())
            EventLagret.UngSak(
                nøkkelId = indeks.toLong(),
                eksternId = eksternId,
                eksternVersjon = eventTid.toString(),
                eventJson = LosObjectMapper.instance.writeValueAsString(node),
                opprettet = eventTid,
                dirty = false,
            )
        }
    }

    private fun map(indeks: Int): OppgaveDto {
        val innsending = SakEventTilOppgaveMapper.lagOppgaveDto(eventer[indeks], null, indeks)
        assertThat(innsending).isInstanceOf(NyOppgaveversjon::class)
        return innsending.dto
    }

    private fun List<OppgaveFeltverdiDto>.verdierFor(nøkkel: String): List<String?> =
        filter { it.nøkkel == nøkkel }.map { it.verdi }

    private fun List<OppgaveFeltverdiDto>.verdiFor(nøkkel: String): String? =
        single { it.nøkkel == nøkkel }.verdi

    @Test
    fun `eventserien inneholder fem eventer`() {
        assertThat(eventer).hasSize(5)
    }

    @Test
    fun `felles feltverdier for alle eventer i serien`() {
        eventer.indices.forEach { indeks ->
            val feltverdier = map(indeks).feltverdier

            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.UUID)).isEqualTo(eksternId)
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Sak.AKTOR_ID)).isEqualTo(aktørId)
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Sak.FAGSYSTEM)).isEqualTo("UNG_SAK")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Sak.SAKSNUMMER)).isEqualTo("300DiQ")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.RESULTATTYPE)).isEqualTo("IKKE_FASTSATT")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Vedtak.YTELSESTYPE)).isEqualTo("AVP")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Vedtak.BEHANDLENDE_ENHET)).isEqualTo("0387")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.TYPEKODE)).isEqualTo("BT-004")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Sak.MOTTATT_DATO)).isEqualTo("2026-06-30T09:28:07")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Sak.REGISTRERT_DATO)).isEqualTo("2026-06-30T09:28:07")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Vedtak.DATO)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Soknad.NYE_KRAV)).isEqualTo("false")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Saksbehandling.ANSVARLIG_SAKSBEHANDLER)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Saksbehandling.ANSVARLIG_BESLUTTER)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Vedtak.TOTRINNSKONTROLL)).isEqualTo("false")
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Ventetid.AKTIV_ARSAK)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Ventetid.AKTIV_FRIST)).isEqualTo(null)
            assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Behandling.ARSAK)).containsOnly("ENDRET-BOSTED")
            assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.AVBRUTT)).containsOnly(null)
        }
    }

    @Test
    fun `ligger ikke hos beslutter før nav-kontor beslutter vilkår`() {
        (0..3).forEach { indeks ->
            val feltverdier = map(indeks).feltverdier
            assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Saksbehandling.LIGGER_HOS_BESLUTTER)).isEqualTo("false")
            assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER))
                .isEqualTo(emptyList<String?>())
        }
    }

    @Test
    fun `event 1 - innhent registeropplysninger uten aksjonspunkt gir uavklart oppgave`() {
        val dto = map(0)

        assertThat(dto.eksternVersjon).isEqualTo("2026-06-30T09:28:08.729578048")
        assertThat(dto.status).isEqualTo(Oppgavestatus.UAVKLART)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STATUS)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STEG)).isEqualTo("INREG")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.ALLE)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.LOSBART)).isEqualTo(emptyList<String?>())
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.UTFORT)).containsOnly(null)
    }

    @Test
    fun `event 2 - vurder fakta om bosted gir åpen oppgave`() {
        val dto = map(1)

        assertThat(dto.eksternVersjon).isEqualTo("2026-06-30T09:28:09.788529594")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STATUS)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STEG)).isEqualTo("VURDER_FAKTA_OM_BOSTED")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.ALLE)).containsOnly("5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE)).containsOnly("5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.LOSBART)).containsOnly("5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.UTFORT)).containsOnly(null)
    }

    @Test
    fun `event 3 - vurder bostedvilkår gir åpen oppgave`() {
        val dto = map(2)

        assertThat(dto.eksternVersjon).isEqualTo("2026-08-21T11:24:00.610608402")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STATUS)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STEG)).isEqualTo("VURDER_BOSTEDVILKÅR")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.ALLE))
            .containsExactlyInAnyOrder("5140", "5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE)).containsOnly("5140")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.LOSBART)).containsOnly("5140")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.UTFORT)).containsOnly("5130")
    }

    @Test
    fun `event 4 - nav-kontor foreslår vilkår gir åpen oppgave`() {
        val dto = map(3)

        assertThat(dto.eksternVersjon).isEqualTo("2026-08-21T11:24:56.369859542")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STATUS)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STEG)).isEqualTo("NAV_KONTOR_FORESLÅ_VILKÅR_STEG")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.ALLE))
            .containsExactlyInAnyOrder("5100", "5130", "5140")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE)).containsOnly("5100")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.LOSBART)).containsOnly("5100")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.UTFORT))
            .containsExactlyInAnyOrder("5130", "5140")
    }

    @Test
    fun `event 5 - nav-kontor beslutter vilkår gir åpen oppgave hos beslutter`() {
        val dto = map(4)

        assertThat(dto.eksternVersjon).isEqualTo("2026-08-21T12:44:50.221793894")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)
        assertThat(dto.reservasjonsnøkkel).endsWith("_beslutter")

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STATUS)).isEqualTo("NAV_KONTOR_BESLUTTER_VILKÅR")
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Behandling.STEG)).isEqualTo("NAV_KONTOR_BESLUTTE_VILKÅR_STEG")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.ALLE))
            .containsExactlyInAnyOrder("5200", "5130", "5140", "5100")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.AKTIVE)).containsOnly("5200")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.LOSBART)).containsOnly("5200")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.FREMTIDIG)).containsOnly(null)
        assertThat(feltverdier.verdiFor(AktivitetspengerFeltIder.Saksbehandling.LIGGER_HOS_BESLUTTER)).isEqualTo("true")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER))
            .containsOnly("2026-08-21T12:44:50.221793894")
        assertThat(feltverdier.verdierFor(AktivitetspengerFeltIder.Aksjonspunkt.UTFORT))
            .containsExactlyInAnyOrder("5130", "5140", "5100")
    }
}



