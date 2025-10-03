package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn

data class StatuskortDto(
    val tittel: KodeOgNavn,
    val topplinje: StatuslinjeDto,
    val linjer: List<StatuslinjeDto> //funksjonell sortering
)

data class StatuslinjeDto(
    val visningsnavn: String,
    val verdi: Long,
    val kildespørring: OppgaveQuery,
)
