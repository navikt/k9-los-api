package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregatfunksjon
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class StatusService(
    private val queryService: OppgaveQueryService,
) {
    private val log: Logger = LoggerFactory.getLogger(StatusService::class.java)

    private val punsjtyper = setOf(
        BehandlingType.PAPIRSØKNAD,
        BehandlingType.DIGITAL_SØKNAD,
        BehandlingType.PAPIRETTERSENDELSE,
        BehandlingType.PAPIRINNTEKTSOPPLYSNINGER,
        BehandlingType.DIGITAL_ETTERSENDELSE,
        BehandlingType.INNLOGGET_CHAT,
        BehandlingType.SKRIV_TIL_OSS_SPØRMSÅL,
        BehandlingType.SKRIV_TIL_OSS_SVAR,
        BehandlingType.SAMTALEREFERAT,
        BehandlingType.KOPI,
        BehandlingType.INNTEKTSMELDING_UTGÅTT,
        BehandlingType.UTEN_FNR_DNR,
        BehandlingType.PUNSJOPPGAVE_IKKE_LENGER_NØDVENDIG,
        BehandlingType.UKJENT,
    )

    fun hentStatus(harTilgangTilKode6: Boolean): List<StatusDto> {
        val filtere = buildList {
            add(FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.IN, listOf(Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode)))
            if (!harTilgangTilKode6) {
                add(FeltverdiOppgavefilter(null, "personbeskyttelse", EksternFeltverdiOperator.EQUALS, listOf("UTEN_KODE6")))
            }
        }
        val oppgaveQuery = OppgaveQuery(
            filtere = filtere,
            select = listOf(
                EnkelSelectFelt("K9", "behandlingTypekode"),
                AggregertSelectFelt(Aggregatfunksjon.COUNT),
            ),
        )
        val gruppert = queryService.queryForGruppering(QueryRequest(oppgaveQuery))

        val alleGrupper = gruppert.mapNotNull { rad ->
            val behandlingTypeKode = rad.grupperingsverdier.firstOrNull()?.verdi?.toString() ?: return@mapNotNull null
            val behandlingType = BehandlingType.fraKode(behandlingTypeKode)
            behandlingType to rad.antall.toInt()
        }

        val (punsjGrupper, andreGrupper) = alleGrupper.partition { it.first in punsjtyper }

        return buildList {
            add(StatusDto("Åpne behandlinger", andreGrupper.sumOf { it.second }))
            addAll(andreGrupper.map { StatusDto(it.first.navn, it.second) })
            if (punsjGrupper.isNotEmpty()) {
                add(StatusDto("Punsj", punsjGrupper.sumOf { it.second }))
            }
        }
    }
}