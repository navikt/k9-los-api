package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat

data class UttrekkResultatRespons(
    val kolonner: List<String>,
    val rader: List<OppgaveResultat>,
    val totaltAntall: Int,
    val offset: Int,
    val limit: Int?
)