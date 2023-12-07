package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import org.junit.jupiter.api.Test

class FeltdefinisjonTest {
    private val område = Område(eksternId = "K9")
    @Test
    fun `test at vi legger til feltdefinisjoner om de ikke finnes fra før`() {
        val innkommendeFeltdefinisjoner = lagFeltdefinisjoner()
        val (sletteListe, oppdaterListe, leggTilListe) = Feltdefinisjoner(område = område, emptySet()).finnForskjeller(innkommendeFeltdefinisjoner)
        assertThat(leggTilListe).hasSize(2)
        assertThat(sletteListe).isEmpty()
    }

    @Test
    fun `test at vi sletter en feltdefinisjon dersom den ikke finnes i dto men er persistert`() {
        val innkommendeFeltdefinisjoner = Feltdefinisjoner(
            område = område,
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    område = område,
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "String",
                    visTilBruker = true,
                    kokriterie = false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                )
            )
        )
        val (sletteListe, oppdaterListe, leggTilListe) = lagFeltdefinisjoner().finnForskjeller(innkommendeFeltdefinisjoner)
        assertThat(sletteListe).hasSize(1)
        assertThat(leggTilListe).isEmpty()
    }

    @Test
    fun `test at vi oppdaterer feltdefinisjoner om de har endringer`() {
        val innkommendeFeltdefinisjoner = Feltdefinisjoner(
            område = område,
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    område = område,
                    visningsnavn = "Test",
                    listetype = true,
                    tolkesSom = "String",
                    visTilBruker = true,
                    kokriterie = false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    område = område,
                    visningsnavn = "Test",
                    listetype = true,
                    tolkesSom = "Date",
                    visTilBruker = true,
                    kokriterie = false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null
                )
            )
        )
        val (sletteListe, oppdaterListe, leggTilListe) = lagFeltdefinisjoner().finnForskjeller(innkommendeFeltdefinisjoner)
        assertThat(sletteListe).hasSize(0)
        assertThat(oppdaterListe).hasSize(2)
        assertThat(leggTilListe).hasSize(0)
    }

    private fun lagFeltdefinisjoner(): Feltdefinisjoner {
        return Feltdefinisjoner(
            område = område,
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    område = område,
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "String",
                    visTilBruker = true,
                    kokriterie = false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    område = område,
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "Date",
                    visTilBruker = true,
                    kokriterie = false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                )
            )
        )
    }

}