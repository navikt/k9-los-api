package no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonSubTypes
import java.time.LocalDateTime
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "hendelseType")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(name = "KRAVDOKUMENT", value = ProduksjonsstyringDokumentHendelseKontrakt::class),
        JsonSubTypes.Type(name = "AKSJONSPUNKT", value = ProduksjonsstyringAksjonspunktHendelseKontrakt::class),
        JsonSubTypes.Type(name = "BEHANDLING", value = ProduksjonsstyringBehandlingHendelseKontrakt::class)
    ]
)
abstract class ProduksjonsstyringHendelseKontrakt(
    open val eksternId: UUID,
    open val hendelseTid: LocalDateTime,
    val hendelseType: K9SakHendelseType,
) {
    fun tryggToString(): String {
        return "K9SakHendelse = " +
                "EksternId: " + eksternId + ", " +
                "HendelseType: " + hendelseType + ", " +
                "HendelseTid: " + hendelseTid
    }
}