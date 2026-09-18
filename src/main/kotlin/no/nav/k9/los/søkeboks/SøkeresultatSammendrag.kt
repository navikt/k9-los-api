package no.nav.k9.los.søkeboks

import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto

sealed class SøkeresultatSammendrag(val type: SøkeresultatType) {
    data object IkkeTilgang : SøkeresultatSammendrag(SøkeresultatType.IKKE_TILGANG)

    data object TomtResultat : SøkeresultatSammendrag(SøkeresultatType.TOMT_RESULTAT)

    data class MedResultat(
        val oppgaver: List<OppgaveSammendragDto>,
    ) : SøkeresultatSammendrag(SøkeresultatType.MED_RESULTAT)
}
