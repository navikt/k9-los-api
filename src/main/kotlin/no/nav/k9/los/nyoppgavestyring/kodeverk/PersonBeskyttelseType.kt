package no.nav.k9.los.nyoppgavestyring.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator

enum class PersonBeskyttelseType (val kode: String, val beskrivelse: String){

    KODE6("KODE6", "Kode 6"),
    UTEN_KODE6("UTEN_KODE6", "Uten kode 6"),
    KODE7_ELLER_EGEN_ANSATT("KODE7_ELLER_EGEN_ANSATT", "Kode 7 eller egen ansatt"),
    UGRADERT("UGRADERT", "Hverken kode 6, kode 7 eller egen ansatt"),
    ;

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): PersonBeskyttelseType {
            return PersonBeskyttelseType.entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}