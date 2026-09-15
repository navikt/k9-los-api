package no.nav.k9.los

@Target(AnnotationTarget.EXPRESSION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
/**
 * Markering for å se hvor det mangler flerområdestøtte. Brukes for å kunne identifisere steder i koden enkelt.
 * */
annotation class ManglerFlerområde(val value: String = "")
