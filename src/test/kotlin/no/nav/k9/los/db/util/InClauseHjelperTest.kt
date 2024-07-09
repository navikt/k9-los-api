package no.nav.k9.los.db.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class InClauseHjelperTest {
    @Test
    fun skal_lage_liste_for_prepared_statement() {
        val verdier = listOf("foo", "bar")
        assertThat(InClauseHjelper.tilParameternavn(verdier, "v")).isEqualTo(":v1,:v2")
        assertThat(InClauseHjelper.parameternavnTilVerdierMap(verdier, "v")).isEqualTo(mapOf("v1" to "foo", "v2" to "bar"))
    }

    @Test
    fun skal_ha_med_cast_i_parameternavnliste_når_spesifisert() {
        val verdier = listOf("foo", "bar")
        assertThat(InClauseHjelper.tilParameternavnMedCast(verdier, "v", castTilType = "mintype")).isEqualTo("cast(:v1 as mintype),cast(:v2 as mintype)")
        assertThat(InClauseHjelper.parameternavnTilVerdierMap(verdier, "v")).isEqualTo(mapOf("v1" to "foo", "v2" to "bar"))
    }

    @Test
    fun skal_begrense_antall_ulike_prepared_statements() {
        // ønsker helst å kunne si ' in(:listenavn) ' for å kunne gjenbruke prepared statement på tvers av alle listelengder, men er ikke støttet i kotliquery
        // begrenser variasjonen i listelengder ved å sende et av elementene flere ganger, og slik kunne gjenbruke prepared statements noe
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 1).map { it }, "x")).isEqualTo(":x1,:x2")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 2).map { it }, "x")).isEqualTo(":x1,:x2")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 3).map { it }, "x")).isEqualTo(":x1,:x2,:x3,:x4")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 4).map { it }, "x")).isEqualTo(":x1,:x2,:x3,:x4")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 5).map { it }, "x")).isEqualTo(":x1,:x2,:x3,:x4,:x5,:x6,:x7,:x8")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 6).map { it }, "x")).isEqualTo(":x1,:x2,:x3,:x4,:x5,:x6,:x7,:x8")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 7).map { it }, "x")).isEqualTo(":x1,:x2,:x3,:x4,:x5,:x6,:x7,:x8")
        assertThat(InClauseHjelper.tilParameternavn(IntRange(1, 8).map { it }, "x")).isEqualTo(":x1,:x2,:x3,:x4,:x5,:x6,:x7,:x8")


        assertThat(InClauseHjelper.parameternavnTilVerdierMap(listOf("en"), "x")).isEqualTo(mapOf(
            "x1" to "en",
            "x2" to "en"
        ))
        assertThat(InClauseHjelper.parameternavnTilVerdierMap(listOf("en", "to", "tre").map { it }, "x")).isEqualTo(
            mapOf(
                "x1" to "en",
                "x2" to "to",
                "x3" to "tre",
                "x4" to "tre", //gjentas
            )
        )
        assertThat(InClauseHjelper.parameternavnTilVerdierMap(listOf("en", "to", "tre", "fire").map { it }, "x")).isEqualTo(
            mapOf(
                "x1" to "en",
                "x2" to "to",
                "x3" to "tre",
                "x4" to "fire",
            )
        )
        assertThat(InClauseHjelper.parameternavnTilVerdierMap(listOf("en", "to", "tre", "fire", "fem").map { it }, "x")).isEqualTo(
            mapOf(
                "x1" to "en",
                "x2" to "to",
                "x3" to "tre",
                "x4" to "fire",
                "x5" to "fem",
                "x6" to "fem", //gjentas
                "x7" to "fem",
                "x8" to "fem",
            )
        )
    }
}