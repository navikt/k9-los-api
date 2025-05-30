package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class StatusService(
    private val queryService: OppgaveQueryService,
    private val oppgaverGruppertRepository: OppgaverGruppertRepository,
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
        val alleGrupper =
            oppgaverGruppertRepository.hentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(harTilgangTilKode6)

        val (punsjGrupper, andreGrupper) = alleGrupper.partition { it.behandlingstype in punsjtyper }

        return buildList {
            add(StatusDto("Åpne behandlinger", alleGrupper.sumOf { it.antall }))
            addAll(andreGrupper.map { StatusDto(it.behandlingstype.navn, it.antall) })
            if (punsjGrupper.isNotEmpty()) {
                add(StatusDto("Punsj", punsjGrupper.sumOf { it.antall }))
            }
        }
    }
}