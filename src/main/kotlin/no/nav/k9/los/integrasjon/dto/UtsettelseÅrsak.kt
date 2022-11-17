package no.nav.k9.los.integrasjon.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

enum class UtsettelseÅrsak {
    ARBEID, LOVBESTEMT_FERIE, SYKDOM, INSTITUSJONSOPPHOLD_SØKER, INSTITUSJONSOPPHOLD_BARNET;

    @JsonCreator
    fun fraKode(@JsonProperty("kode") kode: String): UtsettelseÅrsak? {
        return if (kode == "-") null else valueOf(kode)
    }


    fun gjelderSykdom(): Boolean {
        return this == SYKDOM || this == INSTITUSJONSOPPHOLD_SØKER || this == INSTITUSJONSOPPHOLD_BARNET
    }

}