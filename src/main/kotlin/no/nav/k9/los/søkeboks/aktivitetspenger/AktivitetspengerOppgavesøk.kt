package no.nav.k9.los.søkeboks.aktivitetspenger

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.søkeboks.Oppgavesøk
import no.nav.k9.los.søkeboks.Søkeord

class AktivitetspengerOppgavesøk : Oppgavesøk {
    override fun lagQuery(søkeord: Søkeord): Nothing = ikkeImplementert("søk")

    override fun aktørId(oppgave: Oppgave): Nothing = ikkeImplementert("aktørId")

    override fun saksnummer(oppgave: Oppgave): Nothing = ikkeImplementert("saksnummer")

    override fun erSynlig(oppgave: Oppgave) = true

    override fun tilSammendrag(oppgave: Oppgave, person: PersonPdl?): OppgaveSammendragDto =
        ikkeImplementert("sammendrag")

    private fun ikkeImplementert(hva: String): Nothing =
        throw NotImplementedError("Oppgavesøk for område AKTIVITETSPENGER er ikke implementert ennå ($hva)")
}
