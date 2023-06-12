package no.nav.k9.los.nyoppgavestyring.query.dto.query

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeName


interface IFeltverdiOppgavefilter<T> {
    val område: String?
    val kode: String
    val operator: String
    val verdi: List<T>
}

/**
 * Et filter som sjekker feltverdiene på en oppgave.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
@JsonTypeName("feltverdi")
data class FeltverdiOppgavefilter (
    override val område: String?,
    override val kode: String,
    override val operator: String,

    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    override val verdi: List<Any>
): Oppgavefilter(), IFeltverdiOppgavefilter<Any>