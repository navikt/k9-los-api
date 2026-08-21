package no.nav.k9.los.kodeverk

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class KøKriterierFeltType(@JsonValue val kode: String) {
    BELØP("BELOP"), KODEVERK("KODEVERK"), FLAGG("FLAGG")
}

