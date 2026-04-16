package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator

class StatusService(
    private val queryService: OppgaveQueryService,
) {
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
                AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
            ),
        )
        val resultat = queryService.query(QueryRequest(oppgaveQuery))
        val gruppert = (resultat as no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat.AggregertResultat).rader

        val alleGrupper = gruppert.mapNotNull { rad ->
            val behandlingTypeKode = rad.feltverdier.firstOrNull()?.verdi?.toString() ?: return@mapNotNull null
            val behandlingType = BehandlingType.fraKode(behandlingTypeKode)
            val antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi) as Long
            behandlingType to antall.toInt()
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
