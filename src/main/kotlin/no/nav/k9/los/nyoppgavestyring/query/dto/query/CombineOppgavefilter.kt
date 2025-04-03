package no.nav.k9.los.nyoppgavestyring.query.dto.query

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
@JsonTypeName("combine")
data class CombineOppgavefilter (
    val combineOperator: CombineOperator,
    val filtere: List<Oppgavefilter>
): Oppgavefilter()