package no.nav.k9.fagsystem.k9punsj.kontrakt

import com.fasterxml.jackson.annotation.*
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
@JsonTypeName(K9PunsjHendelseType.PUNSJ_FERDIGSTILT_TYPE)
class ProduksjonsstyringOppgaveFerdigstiltHendelse(
    @JsonProperty("eksternId") eksternId: UUID,
    @JsonProperty("hendelseTid") hendelseTid: LocalDateTime,
    @JsonProperty("journalpostId") journalpostId: JournalpostId,
    @JsonProperty("sendtInn") val sendtInn : Boolean,
    @JsonProperty("ferdigstiltAv") val ferdigstiltAv: String? = null,
) : ProduksjonsstyringHendelse(eksternId, journalpostId, hendelseTid) {

    override fun safeToString() = """
        ProduksjonsstyringOppgaveFerdigstiltHendelse(eksternId=$eksternId, 
        journalpostId=$journalpostId, 
        hendelseTid=$hendelseTid, 
        sendtInn=$sendtInn,
        ferdigstiltAv=$ferdigstiltAv)
        """.trimIndent()
}