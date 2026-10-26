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
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFelter
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

            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.UUID.id)).isEqualTo(eksternId)
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.AKTOR_ID.id)).isEqualTo(aktørId)
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.FAGSYSTEM.id)).isEqualTo("UNG_SAK")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.SAKSNUMMER.id)).isEqualTo("300DiQ")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.RESULTATTYPE.id)).isEqualTo("IKKE_FASTSATT")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.YTELSESTYPE.id)).isEqualTo("AKT")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.BEHANDLENDE_ENHET.id)).isEqualTo("0387")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.TYPEKODE.id)).isEqualTo("BT-004")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.MOTTATT_DATO.id)).isEqualTo("2026-06-30T09:28:07")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Sak.REGISTRERT_DATO.id)).isEqualTo("2026-06-30T09:28:07")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.DATO.id)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Soknad.NYE_KRAV.id)).isEqualTo("false")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_SAKSBEHANDLER.id)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.ANSVARLIG_BESLUTTER.id)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Vedtak.TOTRINNSKONTROLL.id)).isEqualTo("false")
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_ARSAK.id)).isEqualTo(null)
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Ventetid.AKTIV_FRIST.id)).isEqualTo(null)
            assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Behandling.ARSAK.id)).containsOnly("ENDRET-BOSTED")
            assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AVBRUTT.id)).containsOnly(null)
        }
    }

    @Test
    fun `ligger ikke hos beslutter før nav-kontor beslutter vilkår`() {
        (0..3).forEach { indeks ->
            val feltverdier = map(indeks).feltverdier
            assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.LIGGER_HOS_BESLUTTER.id)).isEqualTo("false")
            assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER.id))
                .isEqualTo(emptyList<String?>())
        }
    }

    @Test
    fun `event 1 - innhent registeropplysninger uten aksjonspunkt gir uavklart oppgave`() {
        val dto = map(0)

        assertThat(dto.eksternVersjon).isEqualTo("2026-06-30T09:28:08.729578048")
        assertThat(dto.status).isEqualTo(Oppgavestatus.UAVKLART)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STATUS.id)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STEG.id)).isEqualTo("INREG")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).isEqualTo(emptyList<String?>())
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id)).containsOnly(null)
    }

    @Test
    fun `event 2 - vurder fakta om bosted gir åpen oppgave`() {
        val dto = map(1)

        assertThat(dto.eksternVersjon).isEqualTo("2026-06-30T09:28:09.788529594")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STATUS.id)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STEG.id)).isEqualTo("VURDER_FAKTA_OM_BOSTED")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id)).containsOnly("5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id)).containsOnly("5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly("5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id)).containsOnly(null)
    }

    @Test
    fun `event 3 - vurder bostedvilkår gir åpen oppgave`() {
        val dto = map(2)

        assertThat(dto.eksternVersjon).isEqualTo("2026-08-21T11:24:00.610608402")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STATUS.id)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STEG.id)).isEqualTo("VURDER_BOSTEDVILKÅR")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id))
            .containsExactlyInAnyOrder("5140", "5130")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id)).containsOnly("5140")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly("5140")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id)).containsOnly("5130")
    }

    @Test
    fun `event 4 - nav-kontor foreslår vilkår gir åpen oppgave`() {
        val dto = map(3)

        assertThat(dto.eksternVersjon).isEqualTo("2026-08-21T11:24:56.369859542")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STATUS.id)).isEqualTo("UTRED")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STEG.id)).isEqualTo("NAV_KONTOR_FORESLÅ_VILKÅR_STEG")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id))
            .containsExactlyInAnyOrder("5100", "5130", "5140")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id)).containsOnly("5100")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly("5100")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id))
            .containsExactlyInAnyOrder("5130", "5140")
    }

    @Test
    fun `event 5 - nav-kontor beslutter vilkår gir åpen oppgave hos beslutter`() {
        val dto = map(4)

        assertThat(dto.eksternVersjon).isEqualTo("2026-08-21T12:44:50.221793894")
        assertThat(dto.status).isEqualTo(Oppgavestatus.AAPEN)
        assertThat(dto.reservasjonsnøkkel).endsWith("_beslutter")

        val feltverdier = dto.feltverdier
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STATUS.id)).isEqualTo("NAV_KONTOR_BESLUTTER_VILKÅR")
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Behandling.STEG.id)).isEqualTo("NAV_KONTOR_BESLUTTE_VILKÅR_STEG")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.ALLE.id))
            .containsExactlyInAnyOrder("5200", "5130", "5140", "5100")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.AKTIVE.id)).containsOnly("5200")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.LOSBART.id)).containsOnly("5200")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.FREMTIDIG.id)).containsOnly(null)
        assertThat(feltverdier.verdiFor(AktivitetspengerFelter.Saksbehandling.LIGGER_HOS_BESLUTTER.id)).isEqualTo("true")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Saksbehandling.TID_FORSTE_GANG_HOS_BESLUTTER.id))
            .containsOnly("2026-08-21T12:44:50")
        assertThat(feltverdier.verdierFor(AktivitetspengerFelter.Aksjonspunkt.UTFORT.id))
            .containsExactlyInAnyOrder("5130", "5140", "5100")
    }
}



