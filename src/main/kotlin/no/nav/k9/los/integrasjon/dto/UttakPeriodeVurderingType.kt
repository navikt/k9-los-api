package no.nav.k9.los.integrasjon.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
enum class UttakPeriodeVurderingType {
    PERIODE_OK, PERIODE_OK_ENDRET, PERIODE_KAN_IKKE_AVKLARES, PERIODE_IKKE_VURDERT;

    @JsonCreator
    fun fraKode(@JsonProperty("kode") kode: String): UttakPeriodeVurderingType? {
        return if (kode == "-") null else valueOf(kode)
    }

    fun erOmsøktOgIkkeAvklart(uttakPeriodeVurderingType: UttakPeriodeVurderingType): Boolean {
        return uttakPeriodeVurderingType == PERIODE_IKKE_VURDERT
    }
}
