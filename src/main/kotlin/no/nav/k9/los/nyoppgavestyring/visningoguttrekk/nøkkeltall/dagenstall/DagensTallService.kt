package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
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
        val grupper = listOf(
            DagensTallHovedgruppe.OMSORGSPENGER,
            DagensTallHovedgruppe.OMSORGSDAGER,
            DagensTallHovedgruppe.PLEIEPENGER_SYKT_BARN,
            DagensTallHovedgruppe.PPN,
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
                nyeIDag = hentNye(
                    dato = LocalDate.now(),
                ),
                ferdigstilteIDag = hentFerdigstilte(
                    dato = LocalDate.now(),
                ),
                nyeSiste7Dager = hentNye(
                    dato = LocalDate.now().minusDays(7),
                ),
                ferdigstilteSiste7Dager = hentFerdigstilte(
                    dato = LocalDate.now().minusDays(7),
                )
            )
        )

        for (behandlingType in behandlingstyper) {
            tall.add(
                DagensTallDto(
                    hovedgruppe = DagensTallHovedgruppe.ALLE,
                    undergruppe = DagensTallUndergruppe.fraBehandlingType(behandlingType),
                    nyeIDag = hentNye(
                        dato = LocalDate.now(),
                        behandlingType = behandlingType,
                    ),
                    ferdigstilteIDag = hentFerdigstilte(
                        dato = LocalDate.now(),
                        behandlingType = behandlingType,
                    ),
                    nyeSiste7Dager = hentNye(
                        dato = LocalDate.now().minusDays(7),
                        behandlingType = behandlingType,
                    ),
                    ferdigstilteSiste7Dager = hentFerdigstilte(
                        dato = LocalDate.now().minusDays(7),
                        behandlingType = behandlingType,
                    )
                )
            )
        }

        for (hovedgruppe in grupper) {

            // Totalt for ytelse
            tall.add(
                DagensTallDto(
                    hovedgruppe = hovedgruppe,
                    undergruppe = DagensTallUndergruppe.TOTALT,
                    nyeIDag = hentNye(
                        dato = LocalDate.now(),
                        ytelser = hovedgruppe.ytelser,
                    ),
                    ferdigstilteIDag = hentFerdigstilte(
                        dato = LocalDate.now(),
                        ytelser = hovedgruppe.ytelser,
                    ),
                    nyeSiste7Dager = hentNye(
                        dato = LocalDate.now().minusDays(7),
                        ytelser = hovedgruppe.ytelser,
                    ),
                    ferdigstilteSiste7Dager = hentFerdigstilte(
                        dato = LocalDate.now().minusDays(7),
                        ytelser = hovedgruppe.ytelser,
                    )
                )
            )

            for (behandlingType in behandlingstyper) {
                tall.add(
                    DagensTallDto(
                        hovedgruppe = hovedgruppe,
                        undergruppe = DagensTallUndergruppe.fraBehandlingType(behandlingType),
                        nyeIDag = hentNye(
                            dato = LocalDate.now(),
                            ytelser = hovedgruppe.ytelser,
                            behandlingType = behandlingType,
                        ),
                        ferdigstilteIDag = hentFerdigstilte(
                            dato = LocalDate.now(),
                            ytelser = hovedgruppe.ytelser,
                            behandlingType = behandlingType,
                        ),
                        nyeSiste7Dager = hentNye(
                            dato = LocalDate.now().minusDays(7),
                            ytelser = hovedgruppe.ytelser,
                            behandlingType = behandlingType,
                        ),
                        ferdigstilteSiste7Dager = hentFerdigstilte(
                            dato = LocalDate.now().minusDays(7),
                            ytelser = hovedgruppe.ytelser,
                            behandlingType = behandlingType,
                        )
                    )
                )
                tall.add(
                    DagensTallDto(
                        hovedgruppe = hovedgruppe,
                        undergruppe = DagensTallUndergruppe.forPunsj(),
                        nyeIDag = hentNye(
                            dato = LocalDate.now(),
                            ytelser = hovedgruppe.ytelser,
                            oppgavetype = "k9punsj",
                        ),
                        ferdigstilteIDag = hentFerdigstilte(
                            dato = LocalDate.now(),
                            ytelser = hovedgruppe.ytelser,
                            oppgavetype = "k9punsj",
                        ),
                        nyeSiste7Dager = hentNye(
                            dato = LocalDate.now().minusDays(7),
                            ytelser = hovedgruppe.ytelser,
                            oppgavetype = "k9punsj",
                        ),
                        ferdigstilteSiste7Dager = hentFerdigstilte(
                            dato = LocalDate.now().minusDays(7),
                            ytelser = hovedgruppe.ytelser,
                            oppgavetype = "k9punsj",
                        ),
                    )
                )
            }
        }

        // Punsj
        tall.add(
            DagensTallDto(
                hovedgruppe = DagensTallHovedgruppe.PUNSJ,
                undergruppe = DagensTallUndergruppe.TOTALT,
                nyeIDag = hentNye(
                    dato = LocalDate.now(),
                    oppgavetype = "k9punsj",
                ),
                ferdigstilteIDag = hentFerdigstilte(
                    dato = LocalDate.now(),
                    oppgavetype = "k9punsj",
                ),
                nyeSiste7Dager = hentNye(
                    dato = LocalDate.now().minusDays(7),
                    oppgavetype = "k9punsj",
                ),
                ferdigstilteSiste7Dager = hentFerdigstilte(
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

    private fun hentNye(
        dato: LocalDate,
        ytelser: List<FagsakYtelseType>? = null,
        behandlingType: BehandlingType? = null,
        oppgavetype: String? = null
    ): Long {
        return hentMottattDatoForLukkedeTall(dato, ytelser, behandlingType, oppgavetype) +
                hentÅpneVenterTall(dato, ytelser, behandlingType, oppgavetype)
    }

    private fun hentMottattDatoForLukkedeTall(
        dato: LocalDate,
        ytelser: List<FagsakYtelseType>? = null,
        behandlingType: BehandlingType? = null,
        oppgavetype: String? = null
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOfNotNull(
                        FeltverdiOppgavefilter(
                            null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode)
                        ),
                        FeltverdiOppgavefilter(
                            null,
                            "ferdigstiltDato",
                            EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                            listOf(dato.toString())
                        ),
                        FeltverdiOppgavefilter(
                            "K9",
                            "mottattDato",
                            EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                            listOf(dato.toString())
                        ),
                        ytelser?.let { liste: List<FagsakYtelseType> ->
                            FeltverdiOppgavefilter(
                                "K9", "ytelsestype", EksternFeltverdiOperator.IN, liste.map { it.kode }
                            )
                        },
                        behandlingType?.let {
                            FeltverdiOppgavefilter(
                                "K9",
                                "behandlingTypekode",
                                EksternFeltverdiOperator.EQUALS,
                                listOf(it.kode)
                            )
                        },
                        oppgavetype?.let {
                            FeltverdiOppgavefilter(
                                null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf(it)
                            )
                        },
                    )
                ),
            )
        )
    }

    private fun hentÅpneVenterTall(
        dato: LocalDate,
        ytelser: List<FagsakYtelseType>? = null,
        behandlingType: BehandlingType? = null,
        oppgavetype: String? = null
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOfNotNull(
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavestatus",
                            EksternFeltverdiOperator.IN,
                            listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER).map { it.kode }
                        ),
                        FeltverdiOppgavefilter(
                            "K9",
                            "mottattDato",
                            EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                            listOf(dato.toString())
                        ),
                        ytelser?.let { liste ->
                            FeltverdiOppgavefilter(
                                "K9", "ytelsestype", EksternFeltverdiOperator.IN, liste.map { it.kode }
                            )
                        },
                        behandlingType?.let {
                            FeltverdiOppgavefilter(
                                "K9",
                                "behandlingTypekode",
                                EksternFeltverdiOperator.EQUALS,
                                listOf(it.kode)
                            )
                        },
                        oppgavetype?.let {
                            FeltverdiOppgavefilter(
                                null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf(it)
                            )
                        },
                    )
                ),
            )
        )
    }

    private fun hentFerdigstilte(
        dato: LocalDate,
        ytelser: List<FagsakYtelseType>? = null,
        behandlingType: BehandlingType? = null,
        oppgavetype: String? = null
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOfNotNull(
                        FeltverdiOppgavefilter(
                            null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode)
                        ),
                        FeltverdiOppgavefilter(
                            null,
                            "ferdigstiltDato",
                            EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                            listOf(dato.toString())
                        ),
                        FeltverdiOppgavefilter(
                            område = "K9",
                            kode = "helautomatiskBehandlet",
                            operator = EksternFeltverdiOperator.EQUALS,
                            listOf(false.toString())
                        ),
                        ytelser?.let { liste ->
                            FeltverdiOppgavefilter(
                                "K9", "ytelsestype", EksternFeltverdiOperator.IN, liste.map { it.kode }
                            )
                        },
                        behandlingType?.let {
                            FeltverdiOppgavefilter(
                                "K9",
                                "behandlingTypekode",
                                EksternFeltverdiOperator.EQUALS,
                                listOf(it.kode)
                            )
                        },
                        oppgavetype?.let {
                            FeltverdiOppgavefilter(
                                null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf(it)
                            )
                        },
                    )
                ),
            )
        )
    }
}