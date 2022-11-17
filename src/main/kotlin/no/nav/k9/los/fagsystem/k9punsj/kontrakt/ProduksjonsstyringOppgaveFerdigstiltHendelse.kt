package no.nav.k9.los.fagsystem.k9punsj.kontrakt

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
    eksternId: UUID,
    hendelseTid: LocalDateTime,
    journalpostId: JournalpostId,
    val sendtInn : Boolean,
    val ferdigstiltAv: String? = null,
) : ProduksjonsstyringHendelse(eksternId, journalpostId, hendelseTid) {

    override fun safeToString() = """
        ProduksjonsstyringOppgaveFerdigstiltHendelse(eksternId=$eksternId, 
        journalpostId=$journalpostId, 
        hendelseTid=$hendelseTid, 
        sendtInn=$sendtInn,
        ferdigstiltAv=$ferdigstiltAv)
        """.trimIndent()
}