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
@JsonTypeName(K9PunsjHendelseType.PUNSJ_AVBRUTT_TYPE)
class ProduksjonsstyringOppgaveAvbruttHendelse(
    @JsonProperty("eksternId") eksternId: UUID,
    @JsonProperty("journalpostId") journalpostId: JournalpostId,
    @JsonProperty("hendelseTid") hendelseTid: LocalDateTime
) : ProduksjonsstyringHendelse(eksternId, journalpostId, hendelseTid) {

    override fun safeToString() = """
        ProduksjonsstyringOppgaveAvbruttHendelse(eksternId=$eksternId, 
        journalpostId=$journalpostId, 
        hendelseTid=$hendelseTid)
        """.trimIndent()
}