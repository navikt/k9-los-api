package no.nav.k9.los.nyoppgavestyring.ko.dto

import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad

data class NesteOppgaverFraKoDto(
    val kolonner: List<String>,
    val rader: List<Oppgaverad>,
)