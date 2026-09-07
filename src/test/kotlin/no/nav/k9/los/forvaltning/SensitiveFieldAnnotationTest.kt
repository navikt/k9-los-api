package no.nav.k9.los.forvaltning

import no.nav.k9.los.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.domeneadaptere.ung.eventmottak.ungsak.UngSakEventDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensitiveFieldAnnotationTest {

    @Test
    fun `aktoer-id felter i kilde dto skal vaere merket som sensitive`() {
        assertAktorFieldsAreSensitive(K9SakEventDto::class.java)
        assertAktorFieldsAreSensitive(K9KlageEventDto::class.java)
        assertAktorFieldsAreSensitive(K9PunsjEventDto::class.java)
        assertAktorFieldsAreSensitive(K9TilbakeEventDto::class.java)
        assertAktorFieldsAreSensitive(UngSakEventDto::class.java)
    }


    private fun assertAktorFieldsAreSensitive(source: Class<*>) {
        val aktorFields = source.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .filter { it.lowercase().contains("aktørid") }

        val missingAnnotations = source.declaredFields
            .filterNot { it.isSynthetic }
            .filter { it.name in aktorFields }
            .mapNotNull { field ->
                if (field.getAnnotation(SensitiveField::class.java)?.value == SENSITIVE_FIELDS.AKTOR_ID) null
                else field.name
            }

        assertTrue(
            missingAnnotations.isEmpty(),
            "Felter med aktørId-navn i ${source.simpleName} maa merkes med @SensitiveField(SENSITIVE_FIELDS.AKTOR_ID): $missingAnnotations",
        )
    }
}


