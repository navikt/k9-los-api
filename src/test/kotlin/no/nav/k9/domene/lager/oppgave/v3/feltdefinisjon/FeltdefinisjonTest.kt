package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

class FeltdefinisjonTest {

    @Test
    fun `test at vi legger til feltdefinisjoner om de ikke finnes fra før`() {
        val innkommendeFeltdefinisjoner = lagFeltdefinisjoner()
        val (sletteListe, leggTilListe) = Feltdefinisjoner(område = "K9", emptySet()).finnForskjeller(innkommendeFeltdefinisjoner)
        assertThat(leggTilListe).hasSize(2)
        assertThat(sletteListe).isEmpty()
    }

    @Test
    fun `test at vi sletter en feltdefinisjon dersom den ikke finnes i dto men er persistert`() {
        val innkommendeFeltdefinisjoner = Feltdefinisjoner(
            område = "K9",
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    listetype = false,
                    parsesSom = "String",
                    visTilBruker = true
                )
            )
        )
        val (sletteListe, leggTilListe) = lagFeltdefinisjoner().finnForskjeller(innkommendeFeltdefinisjoner)
        assertThat(sletteListe).hasSize(1)
        assertThat(leggTilListe).isEmpty()
    }

    @Test
    fun `test at vi sletter feltdefinisjoner og legger de til på nytt om de har endringer`() {
        val innkommendeFeltdefinisjoner = Feltdefinisjoner(
            område = "K9",
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    listetype = true,
                    parsesSom = "String",
                    visTilBruker = true
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    listetype = true,
                    parsesSom = "Date",
                    visTilBruker = true
                )
            )
        )
        val (sletteListe, leggTilListe) = lagFeltdefinisjoner().finnForskjeller(innkommendeFeltdefinisjoner)
        assertThat(sletteListe).hasSize(2)
        assertThat(leggTilListe).hasSize(2)
    }

    private fun lagFeltdefinisjoner(): Feltdefinisjoner {
        return Feltdefinisjoner(
            område = "K9",
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    listetype = false,
                    parsesSom = "String",
                    visTilBruker = true
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    listetype = false,
                    parsesSom = "Date",
                    visTilBruker = true
                )
            )
        )
    }

}