package no.nav.k9.los.oppgaveuthenting.omraade.ung

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.omraade.Oppgavesøk
import no.nav.k9.los.oppgaveuthenting.omraade.Søkeord
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

    override fun sakId(oppgave: Oppgave): Nothing = ikkeImplementert("sakId")

    override fun erSynlig(oppgave: Oppgave): Nothing = ikkeImplementert("synlighet")

    override fun tilSammendrag(oppgave: Oppgave, person: PersonPdl?): OppgaveSammendragDto =
        ikkeImplementert("sammendrag")

    private fun ikkeImplementert(hva: String): Nothing =
        throw NotImplementedError("Oppgavesøk for område UNG er ikke implementert ennå ($hva)")
}
