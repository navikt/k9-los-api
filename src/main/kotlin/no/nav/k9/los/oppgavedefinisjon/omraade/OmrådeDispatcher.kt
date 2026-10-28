package no.nav.k9.los.oppgavedefinisjon.omraade

open class OmrådeDispatcher<T : Any>(
    private val k9: T,
    private val aktivitetspenger: T,
    private val ungdomsprogramytelsen: T? = null,
) {
    fun forOmråde(område: Områder): T = when (område) {
        Områder.K9 -> k9
        Områder.AKTIVITETSPENGER -> aktivitetspenger
        Områder.UNGDOMSPROGRAMYTELSEN -> ungdomsprogramytelsen
            ?: throw IllegalArgumentException("Ungdomsprogramytelsen er p.t. kun for statistikkrapportering")
    }
}
