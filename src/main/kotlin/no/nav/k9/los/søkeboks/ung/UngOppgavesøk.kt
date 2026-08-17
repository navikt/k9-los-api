package no.nav.k9.los.søkeboks.ung

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.søkeboks.Oppgavesøk
import no.nav.k9.los.søkeboks.Søkeord
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto

/**
 * Skjelett for område UNG. Feltdefinisjonene for UNG finnes ennå ikke, så det er ikke mulig å
 * si hvilke feltkoder oppgavene har.
 *
 * Klassen finnes for at [no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter] skal kunne kreve
 * en implementasjon per område, og for at det skal være ett greppbart sted som viser hva som
 * gjenstår. Samme mønster som SifAbacPdpKlientUng og IdTokenUng.
 */
class UngOppgavesøk : Oppgavesøk {
    override fun lagQuery(søkeord: Søkeord): Nothing = ikkeImplementert("søk")

    override fun aktørId(oppgave: Oppgave): Nothing = ikkeImplementert("aktørId")

    override fun saksnummer(oppgave: Oppgave): Nothing = ikkeImplementert("saksnummer")

    override fun erSynlig(oppgave: Oppgave) = true

    override fun tilSammendrag(oppgave: Oppgave, person: PersonPdl?): OppgaveSammendragDto =
        ikkeImplementert("sammendrag")

    private fun ikkeImplementert(hva: String): Nothing =
        throw NotImplementedError("Oppgavesøk for område UNG er ikke implementert ennå ($hva)")
}
