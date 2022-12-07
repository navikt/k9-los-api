package no.nav.k9.los.integrasjon.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

enum class OverføringÅrsak {
    INSTITUSJONSOPPHOLD_ANNEN_FORELDER, SYKDOM_ANNEN_FORELDER, IKKE_RETT_ANNEN_FORELDER, ALENEOMSORG;

    @JsonCreator
    fun fraKode(@JsonProperty("kode") kode: String): OverføringÅrsak? {
        return if (kode == "-") null else valueOf(kode)
    }

    fun gjelderSykdom(): Boolean {
        return this == SYKDOM_ANNEN_FORELDER || this == INSTITUSJONSOPPHOLD_ANNEN_FORELDER
    }

}