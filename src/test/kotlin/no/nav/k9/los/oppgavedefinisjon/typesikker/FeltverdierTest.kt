package no.nav.k9.los.oppgavedefinisjon.typesikker

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class FeltverdierTest {

    private object TestFelter : Feltkatalog(Områder.AKTIVITETSPENGER) {
        override val grupper get() = listOf(Gruppe)

        object Gruppe : Feltgruppe() {
            val PÅKREVD = string("påkrevd", "Påkrevd", Synlighet.INTERNT)
            val STATUS = kode("status", "Status", Synlighet.INTERNT, BehandlingStatus::class)
            val AKSJONSPUNKT = kodeliste("aksjonspunkt", "Aksjonspunkt", Synlighet.INTERNT, AksjonspunktDefinisjon::class)
            val TIDSPUNKT = timestamp("tidspunkt", "Tidspunkt", Synlighet.INTERNT)
            val UTENFOR_TYPE = boolean("utenforType", "Utenfor type", Synlighet.INTERNT)
        }
    }

    private object UregistrertGruppe : Feltgruppe() {
        val FELT = string("uregistrert", "Uregistrert", Synlighet.INTERNT)
    }

    private val oppgavetype = Oppgavetypedefinisjon("test", { "$it/" }) {
        påkrevd(TestFelter.Gruppe.PÅKREVD)
        valgfri(TestFelter.Gruppe.STATUS)
        valgfri(TestFelter.Gruppe.AKSJONSPUNKT)
        valgfri(TestFelter.Gruppe.TIDSPUNKT)
    }

    @Test
    fun `verdier serialiseres per felttype`() {
        val tidspunkt = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        val verdier = Feltverdier(oppgavetype)
        verdier[TestFelter.Gruppe.PÅKREVD] = "x"
        verdier[TestFelter.Gruppe.STATUS] = BehandlingStatus.UTREDES
        verdier[TestFelter.Gruppe.AKSJONSPUNKT] = listOf(AksjonspunktDefinisjon.FORESLÅ_VEDTAK, AksjonspunktDefinisjon.FATTER_VEDTAK)
        verdier[TestFelter.Gruppe.TIDSPUNKT] = tidspunkt

        assertThat(verdier.tilDto()).containsExactly(
            OppgaveFeltverdiDto("påkrevd", "x"),
            OppgaveFeltverdiDto("status", BehandlingStatus.UTREDES.kode),
            OppgaveFeltverdiDto("aksjonspunkt", AksjonspunktDefinisjon.FORESLÅ_VEDTAK.kode),
            OppgaveFeltverdiDto("aksjonspunkt", AksjonspunktDefinisjon.FATTER_VEDTAK.kode),
            OppgaveFeltverdiDto("tidspunkt", tidspunkt.toString()),
        )
    }

    @Test
    fun `tom liste gir én nullverdi`() {
        val verdier = Feltverdier(oppgavetype)
        verdier[TestFelter.Gruppe.PÅKREVD] = "x"
        verdier[TestFelter.Gruppe.AKSJONSPUNKT] = emptyList()

        assertThat(verdier.tilDto().filter { it.nøkkel == "aksjonspunkt" }).containsExactly(OppgaveFeltverdiDto("aksjonspunkt", null))
    }

    @Test
    fun `kodefelt deserialiseres tilbake til kodeverdi`() {
        assertThat(TestFelter.Gruppe.STATUS.deserialiser(BehandlingStatus.UTREDES.kode)).isEqualTo(BehandlingStatus.UTREDES)
    }

    @Test
    fun `timestamp trunkeres til sekunder`() {
        val tidspunkt = LocalDateTime.of(2026, 8, 21, 12, 44, 50, 221_793_894)

        assertThat(TestFelter.Gruppe.TIDSPUNKT.serialiser(tidspunkt)).isEqualTo("2026-08-21T12:44:50")
    }

    @Test
    fun `felt utenfor oppgavetypen avvises`() {
        assertThrows<IllegalArgumentException> {
            Feltverdier(oppgavetype)[TestFelter.Gruppe.UTENFOR_TYPE] = true
        }
    }

    @Test
    fun `manglende påkrevd felt avvises`() {
        assertThrows<IllegalArgumentException> { Feltverdier(oppgavetype).tilDto() }
    }

    @Test
    fun `oppgavetype med felt som ikke er i feltkatalogen avvises`() {
        val katalog = object : Oppgavetypekatalog(TestFelter) {
            override val oppgavetyper = listOf(Oppgavetypedefinisjon("ugyldig", { it }) { valgfri(UregistrertGruppe.FELT) })
        }
        assertThrows<IllegalArgumentException> { katalog.tilDto("http://x") }
    }
}

