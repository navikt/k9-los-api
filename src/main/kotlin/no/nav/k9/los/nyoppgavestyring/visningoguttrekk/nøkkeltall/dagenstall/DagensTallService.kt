package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.measureTime


class DagensTallService(
    private val queryService: OppgaveQueryService
) {
    private val cache = Cache<LocalDate, DagensTallResponse>(null)
    private val log: Logger = LoggerFactory.getLogger(DagensTallService::class.java)

    fun hentCachetVerdi(): DagensTallResponse {
        return cache.get(LocalDate.now())?.value ?: DagensTallResponse(null, emptyList(), emptyList(), emptyList())
    }

    fun oppdaterCache(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            cache.removeExpiredObjects(LocalDateTime.now())
            val tidBruktPåOppdatering = measureTime {
                val dagensTall = hentFraDatabase()
                cache.set(LocalDate.now(), CacheObject(dagensTall, LocalDateTime.now().plusDays(1)))
            }
            log.info("Oppdaterte dagens tall på $tidBruktPåOppdatering")
        }
    }

    private fun hentFraDatabase(): DagensTallResponse {
        val ytelser = listOf(
            FagsakYtelseType.OMSORGSPENGER,
            FagsakYtelseType.OMSORGSDAGER,
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            FagsakYtelseType.PPN
        )
        val behandlingstyper = listOf(
            BehandlingType.FORSTEGANGSSOKNAD,
            BehandlingType.KLAGE,
            BehandlingType.REVURDERING,
            BehandlingType.TILBAKE,
        )

        val tall = mutableListOf<DagensTallDto>()

        // Totalt for alle ytelser og behandlingstyper
        tall.add(
            DagensTallDto(
                hovedgruppe = DagensTallHovedgruppe.ALLE,
                undergruppe = DagensTallUndergruppe.TOTALT,
                nyeIDag = hentTall(
                    datotype = Datotype.MOTTATT_DATO,
                    dato = LocalDate.now(),
                ),
                ferdigstilteIDag = hentTall(
                    datotype = Datotype.FERDIGSTILT_DATO,
                    dato = LocalDate.now(),
                ),
                nyeSiste7Dager = hentTall(
                    datotype = Datotype.MOTTATT_DATO,
                    dato = LocalDate.now().minusDays(7),
                ),
                ferdigstilteSiste7Dager = hentTall(
                    datotype = Datotype.FERDIGSTILT_DATO,
                    dato = LocalDate.now().minusDays(7),
                )
            )
        )

        for (behandlingType in behandlingstyper) {
            tall.add(
                DagensTallDto(
                    hovedgruppe = DagensTallHovedgruppe.ALLE,
                    undergruppe = DagensTallUndergruppe.fraBehandlingType(behandlingType),
                    nyeIDag = hentTall(
                        datotype = Datotype.MOTTATT_DATO,
                        dato = LocalDate.now(),
                        behandlingType = behandlingType,
                    ),
                    ferdigstilteIDag = hentTall(
                        datotype = Datotype.FERDIGSTILT_DATO,
                        dato = LocalDate.now(),
                        behandlingType = behandlingType,
                    ),
                    nyeSiste7Dager = hentTall(
                        datotype = Datotype.MOTTATT_DATO,
                        dato = LocalDate.now().minusDays(7),
                        behandlingType = behandlingType,
                    ),
                    ferdigstilteSiste7Dager = hentTall(
                        datotype = Datotype.FERDIGSTILT_DATO,
                        dato = LocalDate.now().minusDays(7),
                        behandlingType = behandlingType,
                    )
                )
            )
        }

        for (ytelseType in ytelser) {
            val hovedgruppe = DagensTallHovedgruppe.fraFagsakYtelseType(ytelseType)

            // Totalt for ytelse
            tall.add(
                DagensTallDto(
                    hovedgruppe = hovedgruppe,
                    undergruppe = DagensTallUndergruppe.TOTALT,
                    nyeIDag = hentTall(
                        datotype = Datotype.MOTTATT_DATO,
                        dato = LocalDate.now(),
                        fagsakYtelseType = ytelseType,
                    ),
                    ferdigstilteIDag = hentTall(
                        datotype = Datotype.FERDIGSTILT_DATO,
                        dato = LocalDate.now(),
                        fagsakYtelseType = ytelseType,
                    ),
                    nyeSiste7Dager = hentTall(
                        datotype = Datotype.MOTTATT_DATO,
                        dato = LocalDate.now().minusDays(7),
                        fagsakYtelseType = ytelseType,
                    ),
                    ferdigstilteSiste7Dager = hentTall(
                        datotype = Datotype.FERDIGSTILT_DATO,
                        dato = LocalDate.now().minusDays(7),
                        fagsakYtelseType = ytelseType,
                    )
                )
            )

            for (behandlingType in behandlingstyper) {
                tall.add(
                    DagensTallDto(
                        hovedgruppe = hovedgruppe,
                        undergruppe = DagensTallUndergruppe.fraBehandlingType(behandlingType),
                        nyeIDag = hentTall(
                            datotype = Datotype.MOTTATT_DATO,
                            dato = LocalDate.now(),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        ),
                        ferdigstilteIDag = hentTall(
                            datotype = Datotype.FERDIGSTILT_DATO,
                            dato = LocalDate.now(),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        ),
                        nyeSiste7Dager = hentTall(
                            datotype = Datotype.MOTTATT_DATO,
                            dato = LocalDate.now().minusDays(7),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        ),
                        ferdigstilteSiste7Dager = hentTall(
                            datotype = Datotype.FERDIGSTILT_DATO,
                            dato = LocalDate.now().minusDays(7),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        )
                    )
                )
            }
        }

        // Punsj
        tall.add(
            DagensTallDto(
                hovedgruppe = DagensTallHovedgruppe.PUNSJ,
                undergruppe = DagensTallUndergruppe.TOTALT,
                nyeIDag = hentTall(
                    datotype = Datotype.MOTTATT_DATO,
                    dato = LocalDate.now(),
                    oppgavetype = "k9punsj",
                ),
                ferdigstilteIDag = hentTall(
                    datotype = Datotype.FERDIGSTILT_DATO,
                    dato = LocalDate.now(),
                    oppgavetype = "k9punsj",
                ),
                nyeSiste7Dager = hentTall(
                    datotype = Datotype.MOTTATT_DATO,
                    dato = LocalDate.now().minusDays(7),
                    oppgavetype = "k9punsj",
                ),
                ferdigstilteSiste7Dager = hentTall(
                    datotype = Datotype.FERDIGSTILT_DATO,
                    dato = LocalDate.now().minusDays(7),
                    oppgavetype = "k9punsj",
                )
            )
        )

        return DagensTallResponse(
            oppdatertTidspunkt = LocalDateTime.now(),
            hovedgrupper = DagensTallHovedgruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            undergrupper = DagensTallUndergruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            tall = tall
        )
    }

    private fun hentTall(
        datotype: Datotype,
        dato: LocalDate,
        fagsakYtelseType: FagsakYtelseType? = null,
        behandlingType: BehandlingType? = null,
        oppgavetype: String? = null
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOfNotNull(
                        fagsakYtelseType?.let {
                            FeltverdiOppgavefilter(
                                "K9", "ytelsestype", EksternFeltverdiOperator.EQUALS.kode, listOf(it.kode)
                            )
                        },
                        behandlingType?.let {
                            FeltverdiOppgavefilter(
                                "K9",
                                "behandlingTypekode",
                                EksternFeltverdiOperator.EQUALS.kode,
                                listOf(it.kode)
                            )
                        },
                        oppgavetype?.let {
                            FeltverdiOppgavefilter(
                                "K9", "oppgavetype", EksternFeltverdiOperator.EQUALS.kode, listOf(it)
                            )
                        },
                        FeltverdiOppgavefilter(datotype.område, datotype.kode, EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS.kode, listOf(dato.toString())),
                    )
                ),
            )
        )
    }

    private enum class Datotype(val område: String?, val kode: String) {
        MOTTATT_DATO("K9", "mottattDato"), FERDIGSTILT_DATO(null, "ferdigstiltDato")
    }
}