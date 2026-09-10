package no.nav.k9.los.forvaltning

enum class SENSITIVE_FIELDS {
    AKTOR_ID,
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SensitiveField(val value: SENSITIVE_FIELDS)

