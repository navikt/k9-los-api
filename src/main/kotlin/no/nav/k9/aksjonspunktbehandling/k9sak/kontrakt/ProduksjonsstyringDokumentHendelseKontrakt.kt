package no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.sak.kontrakt.krav.KravDokumentType
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
data class ProduksjonsstyringDokumentHendelseKontrakt(
    override val eksternId: UUID,
    override val hendelseTid: LocalDateTime,
    val kravdokumenter: List<KravDokumentType>
) : ProduksjonsstyringHendelseKontrakt(eksternId, hendelseTid, K9SakHendelseType.KRAVDOKUMENT)