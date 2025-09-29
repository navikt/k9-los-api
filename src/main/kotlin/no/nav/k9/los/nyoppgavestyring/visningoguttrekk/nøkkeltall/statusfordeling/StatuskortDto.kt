package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn

data class StatuskortDto(
    private val tittel: KodeOgNavn,
    private val topplinje: StatuslinjeDto,
    private val linjer: List<StatuslinjeDto> //funksjonell sortering
)

data class StatuslinjeDto(
    private val visningsnavn: String,
    private val verdi: Long,
    private val kildespørring: OppgaveQuery,
)
