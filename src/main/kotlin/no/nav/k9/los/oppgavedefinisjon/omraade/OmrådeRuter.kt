package no.nav.k9.los.oppgavedefinisjon.omraade

abstract class OmrådeRuter<T : Any>(
    private val k9: T,
    private val ung: T,
) {
    fun forOmråde(område: Områder): T = when (område) {
        Områder.K9 -> k9
        Områder.UNG -> ung
    }

    fun forOmråde(område: Område): T = forOmråde(Områder.fraEksternId(område.eksternId))
}
