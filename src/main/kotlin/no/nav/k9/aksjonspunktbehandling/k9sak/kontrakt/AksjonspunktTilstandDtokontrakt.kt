package no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt.AksjonspunktTilstandDtokontrakt
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import java.time.LocalDateTime

/**
 * Informasjon om aksjonspunktstilstanden i behandlingen.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
class AksjonspunktTilstandDtokontrakt(
    @field:JsonProperty(value = "aksjonspunktKode",required = true) var aksjonspunktKode: String,
    @field:JsonProperty(value = "status", required = true) var status: AksjonspunktStatus,
    @field:JsonProperty(value = "venteårsak") var venteårsak: Venteårsak?,
    @field:JsonProperty(value = "ansvarligSaksbehandler") var ansvarligSaksbehandler: String?,
    @field:JsonProperty(value = "fristTid") var fristTid: LocalDateTime?
) {
    constructor(kopierFra: AksjonspunktTilstandDtokontrakt) : this(
        kopierFra.aksjonspunktKode,
        kopierFra.status,
        kopierFra.venteårsak,
        kopierFra.ansvarligSaksbehandler,
        kopierFra.fristTid
    ) {
    }

    override fun toString(): String {
        return "AksjonspunktTilstandDto{" +
                "aksjonspunktKode='" + aksjonspunktKode + '\'' +
                ", status='" + status + '\'' +
                ", venteårsak='" + venteårsak + '\'' +
                ", ansvarligSaksbehandler='" + ansvarligSaksbehandler + '\'' +
                ", fristTid=" + fristTid +
                '}'
    }
}