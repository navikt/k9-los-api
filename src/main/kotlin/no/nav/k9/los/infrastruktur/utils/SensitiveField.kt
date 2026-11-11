package no.nav.k9.los.infrastruktur.utils

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SensitiveField(val replacement: String = "SKJERMET")
