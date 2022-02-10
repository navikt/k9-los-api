package no.nav.k9.fagsystem.k9sak

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.k9.sak.kontrakt.krav.KravDokumentType
import java.util.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "hendelseType")
@JsonSubTypes(
    JsonSubTypes.Type(name = "nyttDokument", value = NyttDokumentEventDto::class),
)
abstract class K9SakHendelse {
    abstract val eksternId: UUID
}

class NyttDokumentEventDto(
    override val eksternId: UUID,
    val kravDokumenter: List<KravDokumentType>,
) : K9SakHendelse()
