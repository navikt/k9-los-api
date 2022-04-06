package no.nav.k9.fagsystem.k9punsj.kontrakt

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
@JsonTypeName(K9PunsjHendelseType.PUNSJ_OPPRETTET_TYPE)
class ProduksjonsstyringOppgaveOpprettetHendelse(
    eksternId: UUID,
    journalpostId: JournalpostId,
    hendelseTid: LocalDateTime,
    val ytelseType: String?,
    val behandlingType: String?,
    val behandlingstidFrist: LocalDate?,
    val søkersAktørId: AktørId?,
    val pleietrengendeAktørId: AktørId?,
) : ProduksjonsstyringHendelse(eksternId, journalpostId, hendelseTid) {

    override fun safeToString() = """
        PunsjOppgaveOpprettetHendelse(eksternId=$eksternId, 
        journalpostId=$journalpostId, 
        hendelseTid=$hendelseTid, 
        ytelseType=$ytelseType, 
        behandlingType=$behandlingType, 
        frist=$behandlingstidFrist)
        """.trimIndent()
}