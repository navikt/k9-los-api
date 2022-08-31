package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import no.nav.k9.domene.lager.oppgave.v3.omraade.Område
import org.junit.jupiter.api.Test

class FeltdefinisjonTest {
    val område = Område(eksternId = "K9")
    @Test
    fun `test at vi legger til feltdefinisjoner om de ikke finnes fra før`() {
        val innkommendeFeltdefinisjoner = lagFeltdefinisjoner()
        val (sletteListe, leggTilListe) = Feltdefinisjoner(område = område, emptySet()).finnForskjeller(innkommendeFeltdefinisjoner)
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
            område = område,
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    område = område,
                    listetype = true,
                    parsesSom = "String",
                    visTilBruker = true
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    område = område,
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
            område = område,
            feltdefinisjoner = setOf(
                Feltdefinisjon(
                    eksternId = "saksnummer",
                    område = område,
                    listetype = false,
                    parsesSom = "String",
                    visTilBruker = true
                ),
                Feltdefinisjon(
                    eksternId = "opprettet",
                    område = område,
                    listetype = false,
                    parsesSom = "Date",
                    visTilBruker = true
                )
            )
        )
    }

}