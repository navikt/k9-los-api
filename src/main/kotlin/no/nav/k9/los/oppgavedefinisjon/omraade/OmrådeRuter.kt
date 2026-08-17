package no.nav.k9.los.oppgavedefinisjon.omraade

/**
 * Generisk ruting av en tjenestetype til implementasjonen for et område.
 *
 * Rutingen gjøres med et uttømmende `when` framfor et map-oppslag. Et `when` uten else-gren
 * feiler ved kompilering når [Områder] utvides, mens et map-oppslag først feiler i runtime på
 * første request for det nye området. Samme mønster som PepClient.tilgangKlientFor, men samlet
 * ett sted slik at hvert nytt områdedelt konsept slipper å gjenta det.
 *
 * Implementasjonen for hvert område er påkrevd i konstruktøren. Det er med vilje: et nytt
 * område i [Områder] skal både bryte `when`-en her og tvinge hver konkret ruter til å ta
 * stilling, framfor å kunne utelates og feile i produksjon. Områder som ennå ikke er
 * implementert oppgir et skjelett som kaster NotImplementedError, slik SifAbacPdpKlientUng gjør.
 *
 * Bruk en navngitt subklasse per konsept framfor å injisere `OmrådeRuter<Noe>` direkte:
 *
 * ```
 * class Oppgavesøkere(k9: K9Oppgavesøk, ung: UngOppgavesøk) : OmrådeRuter<Oppgavesøk>(k9, ung)
 * ```
 *
 * Det gir lesbare kallsteder, og er nødvendig for Koin, som nøkler bindinger på KClass og
 * dermed ikke skiller `OmrådeRuter<A>` fra `OmrådeRuter<B>`.
 */
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
