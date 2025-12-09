package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi

data class UttrekkResultatRespons(
    val rader: List<List<Oppgavefeltverdi>>,
    val totaltAntall: Int,
    val offset: Int,
    val limit: Int?
)