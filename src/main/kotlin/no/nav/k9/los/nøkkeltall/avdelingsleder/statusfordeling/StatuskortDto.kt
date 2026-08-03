package no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling

import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.nøkkeltall.KodeOgNavn

data class StatuskortDto(
    val tittel: KodeOgNavn,
    val topplinje: StatuslinjeDto,
    val linjer: List<StatuslinjeDto>,
    val bunnlinje: StatuslinjeDto,
)

data class StatuslinjeDto(
    val visningsnavn: String,
    val verdi: Long,
    val kildespørring: OppgaveQuery,
)
