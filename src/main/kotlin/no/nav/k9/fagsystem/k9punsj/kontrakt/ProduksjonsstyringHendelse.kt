package no.nav.k9.fagsystem.k9punsj.kontrakt

import com.fasterxml.jackson.annotation.*
import no.nav.k9.fagsystem.k9punsj.kontrakt.K9PunsjHendelseType.Companion.PUNSJ_AVBRUTT_TYPE
import no.nav.k9.fagsystem.k9punsj.kontrakt.K9PunsjHendelseType.Companion.PUNSJ_FERDIGSTILT_TYPE
import no.nav.k9.fagsystem.k9punsj.kontrakt.K9PunsjHendelseType.Companion.PUNSJ_OPPRETTET_TYPE
import no.nav.k9.sak.typer.JournalpostId
import java.time.LocalDateTime
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "hendelseType", defaultImpl = ProduksjonsstyringHendelse::class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(name = PUNSJ_OPPRETTET_TYPE, value = ProduksjonsstyringOppgaveOpprettetHendelse::class),
        JsonSubTypes.Type(name = PUNSJ_FERDIGSTILT_TYPE, value = ProduksjonsstyringOppgaveFerdigstiltHendelse::class),
        JsonSubTypes.Type(name = PUNSJ_AVBRUTT_TYPE, value = ProduksjonsstyringOppgaveAvbruttHendelse::class)
    ]
)
open class ProduksjonsstyringHendelse(
    @JsonProperty("eksternId") val eksternId: UUID,
    @JsonProperty("journalpostId") val journalpostId: JournalpostId,
    @JsonProperty("hendelseTid") val hendelseTid: LocalDateTime
) {
    open fun safeToString(): String {
        return "ProduksjonsstyringHendelse{" +
                "EksternId: " + eksternId + ", " +
                "HendelseTid: " + hendelseTid +
                "}"
    }
}