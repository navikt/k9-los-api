package no.nav.k9.los.forvaltning

import com.fasterxml.jackson.databind.AnnotationIntrospector
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper

object ForvaltningSensitiveObjectMapper {
    // Dedicated mapper for forvaltning responses: strips fields annotated as sensitive.
    val prettyInstance = LosObjectMapper.prettyInstance.copy().setAnnotationIntrospector(
        AnnotationIntrospector.pair(
            SensitiveFieldIntrospector(),
            LosObjectMapper.prettyInstance.serializationConfig.annotationIntrospector,
        )
    )

    private class SensitiveFieldIntrospector : JacksonAnnotationIntrospector() {
        override fun hasIgnoreMarker(member: AnnotatedMember): Boolean {
            return member.hasAnnotation(SensitiveField::class.java) || super.hasIgnoreMarker(member)
        }
    }
}

