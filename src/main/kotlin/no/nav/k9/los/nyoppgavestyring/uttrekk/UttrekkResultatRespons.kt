package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.query.SelectFelt

data class UttrekkResultatRespons(
    val kolonner: List<SelectFelt>,
    val rader: List<UttrekkRad>,
    val totaltAntall: Int,
    val offset: Int,
    val limit: Int?
)