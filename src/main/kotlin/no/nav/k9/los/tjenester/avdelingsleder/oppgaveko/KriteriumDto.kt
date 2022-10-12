package no.nav.k9.los.tjenester.avdelingsleder.oppgaveko

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.k9.los.domene.modell.KøKriterierType

data class KriteriumDto(
    val id: String,
    val kriterierType: KøKriterierType,
    // Brukes for å inkluder/ekskluder funksjonalitet (ikke i bruk enda)
    val inkluder: Boolean = true,
    val fom: String? = null,
    val tom: String? = null,
    // Brukes for å fjerne et kriterie
    val checked: Boolean? = null,
    val koder: List<String>? = null
) {
    @JsonIgnore
    fun valider() {
        this.kriterierType.validator.valider(this)
    }
}
