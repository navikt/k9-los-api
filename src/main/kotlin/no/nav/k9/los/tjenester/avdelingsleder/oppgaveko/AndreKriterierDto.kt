package no.nav.k9.los.tjenester.avdelingsleder.oppgaveko

import no.nav.k9.los.domene.modell.AndreKriterierType

data class AndreKriterierDto(
    val id: String,
    val andreKriterierType: AndreKriterierType,
    val checked: Boolean,
    val inkluder: Boolean
)
