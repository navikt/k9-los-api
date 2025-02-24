package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class StatusService(
    private val queryService: OppgaveQueryService,
    private val oppgaverGruppertRepository: OppgaverGruppertRepository,
) {
    private val log: Logger = LoggerFactory.getLogger(StatusService::class.java)

    fun hentStatus(harTilgangTilKode6: Boolean): List<OppgaverGruppertRepository.BehandlingstypeAntallDto> {
        val grupperte =
            oppgaverGruppertRepository.hentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(harTilgangTilKode6)
        val (medbehandlingType, utenBehandlingType) = grupperte.partition { it.behandlingstype != null }
        if (utenBehandlingType.isNotEmpty()) {
            val antall = utenBehandlingType.sumOf { it.antall }
            log.warn("Fant $antall oppgaver uten behandlingstype, de blir ikke med oversikt som viser antall")
        }


        val totaltAntall = OppgaverGruppertRepository.BehandlingstypeAntallDto(
            "Åpne behandlinger",
            oppgaverGruppertRepository.hentTotaltAntallÅpneOppgaver(harTilgangTilKode6)
        )

        val punsjtyper = setOf(
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
        var punsjSum = 0
        val mutableList = medbehandlingType.toMutableList()
        medbehandlingType.forEach { antallDto ->
            if (punsjtyper.any { it.kode == antallDto.behandlingstype }) {
                punsjSum += antallDto.antall
                mutableList.removeIf { it.behandlingstype == antallDto.behandlingstype }
            }
        }
        mutableList.add(OppgaverGruppertRepository.BehandlingstypeAntallDto("Punsj", punsjSum))

        return listOf(totaltAntall).plus(mutableList)
    }
}