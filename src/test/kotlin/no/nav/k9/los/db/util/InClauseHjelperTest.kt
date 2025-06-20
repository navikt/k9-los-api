package no.nav.k9.los.db.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
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
}