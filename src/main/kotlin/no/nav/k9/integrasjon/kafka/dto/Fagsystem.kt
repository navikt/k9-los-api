package no.nav.k9.integrasjon.kafka.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat


@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Fagsystem(val kode: String, val kodeverk: String) {
    K9SAK("K9SAK", "FAGSYSTEM"),
    K9TILBAKE("K9TILBAKE", "FAGSYSTEM"),
    PUNSJ("PUNSJ", "FAGSYSTEM"),
    OMSORGSPENGER("OMSORGSPENGER", "FAGSYSTEM");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(kode: String): Fagsystem = values().find { it.kode == kode }!!
    }
}
