package no.nav.k9.los.domene.modell

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.k9.los.nyoppgavestyring.kodeverk.KøKriterierType

data class KriteriumDto(
    val id: String,
    val kriterierType: KøKriterierType,
    // Brukes for å inkluder/ekskluder funksjonalitet (ikke i bruk enda)
    val fom: String? = null,
    val tom: String? = null,
    // Brukes for å fjerne et kriterie
    val checked: Boolean? = null,
    val koder: List<String>? = null,
    // Brukes av boolean kriterier for å inkludere og ekskludere
    val inkluder: Boolean? = null
)