package no.nav.k9.integrasjon.pdl

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.k9.aksjonspunktbehandling.objectMapper
import org.junit.Test
import kotlin.test.assertEquals

class PdlServiceTest {
 
    @Test
    fun `skal desrialisere personpdl`() {        
        val json = "{\n  \"data\": {\n    \"hentPerson\": {\n      \"navn\": [\n        {\n          \"fornavn\": \"GRØNN\",\n          \"mellomnavn\": null,\n          \"etternavn\": \"STAFFELI\",\n          \"forkortetNavn\": \"STAFFELI GRØNN\"\n        }\n      ],\n      \"folkeregisteridentifikator\": [\n        {\n          \"identifikasjonsnummer\": \"19128521618\"\n        }\n      ],\n      \"kjoenn\": [\n        {\n          \"kjoenn\": \"KVINNE\"\n        }\n      ],\n      \"doedsfall\": []\n    }\n  }\n}"
        val readValue = objectMapper().readValue<PersonPdl>(json)
        assertEquals("KVINNE", readValue.data.hentPerson.kjoenn[0].kjoenn)
    }
}