package no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt

import com.fasterxml.jackson.annotation.*
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
data class ProduksjonsstyringAksjonspunktHendelseKontrakt(
    @JsonProperty("eksternId") override val eksternId: UUID,
    @JsonProperty("hendelseTid") override val hendelseTid: LocalDateTime,
    @JsonProperty("aksjonspunktTilstander") val aksjonspunktTilstander: List<AksjonspunktTilstandDtokontrakt>
) : ProduksjonsstyringHendelseKontrakt(eksternId, hendelseTid, K9SakHendelseType.AKSJONSPUNKT)