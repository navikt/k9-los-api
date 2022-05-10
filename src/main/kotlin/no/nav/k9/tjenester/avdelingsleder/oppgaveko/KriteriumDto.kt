package no.nav.k9.tjenester.avdelingsleder.oppgaveko

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.k9.domene.modell.KøKriterierType

data class KriteriumDto(
    val id: String,
    val kriterierType: KøKriterierType,
    val inkluder: Boolean,
    val fom: String? = null,
    val tom: String? = null,
    val checked: Boolean? = null,
    val koder: List<String>? = null
) {
    @JsonIgnore
    fun valider() {
        this.kriterierType.validator.valider(this)
    }
}
