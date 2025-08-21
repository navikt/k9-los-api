package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

class FerdigstiltePerEnhetService(
    enheter: List<String>,
    private val queryService: OppgaveQueryService
) {
    private val parametre = enheter.map { enhet -> FerdigstiltParameter.Enhet(enhet) } + FerdigstiltParameter.Helautomatisk
    private var oppdatertTidspunkt: LocalDateTime? = null
    private val cache = Cache<LocalDate, List<FerdigstiltePerEnhetTall>>(null)
    private val log: Logger = LoggerFactory.getLogger(FerdigstiltePerEnhetService::class.java)

    private val grupper = listOf(
        FerdigstiltePerEnhetGruppe.PLEIEPENGER_SYKT_BARN,
        FerdigstiltePerEnhetGruppe.PPN,
        FerdigstiltePerEnhetGruppe.OMSORGSDAGER,
        FerdigstiltePerEnhetGruppe.OMSORGSPENGER,
    )

    fun hentCachetVerdi(gruppe: FerdigstiltePerEnhetGruppe, uker: Int): FerdigstiltePerEnhetResponse {
        require(uker == 2 || uker == 4) { "Uker må være 2 eller 4" }
        cache.removeExpiredObjects(LocalDateTime.now())

        val idag = LocalDate.now()
        val datoer = idag.minusDays(7L * uker - 1).datesUntil(idag.plusDays(1)).toList()

        return FerdigstiltePerEnhetResponse(
            oppdatertTidspunkt = oppdatertTidspunkt,
            kolonner = datoer.map { it.format(DateTimeFormatter.ofPattern("dd.MM")) },
            serier = parametre.map { parameter ->
                FerdigstiltePerEnhetSerie(
                    navn = parameter.navn,
                    data = datoer.map { dato ->
                        cache.get(
                            dato,
                            LocalDateTime.now()
                        )?.value?.find { it.parameter == parameter && it.gruppe == gruppe }?.antall
                            ?: 0
                    }
                )
            }
        )
    }

    fun oppdaterCache(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            cache.removeExpiredObjects(LocalDateTime.now())

            val idag = LocalDate.now()
            val datoer = idag.minusDays(27).datesUntil(idag)

            var antallDagerHenter = 0
            val tidBruktPåOppdatering = measureTime {
                datoer.forEach { dato ->
                    if (!cache.containsKey(dato, LocalDateTime.now())) {
                        cache.set(dato, CacheObject(hentFraDatabase(dato), dato.plusDays(29).atStartOfDay()))
                        antallDagerHenter++
                    }
                }

                // Tallene for idag er alltid oppdatert
                cache.set(idag, CacheObject(hentFraDatabase(idag), idag.plusDays(29).atStartOfDay()))
                antallDagerHenter++

                oppdatertTidspunkt = LocalDateTime.now()
            }
            log.info("Oppdaterte $antallDagerHenter datoer for ferdigstilte per enhet på $tidBruktPåOppdatering")
        }
    }

    private fun hentFraDatabase(dato: LocalDate): List<FerdigstiltePerEnhetTall> {
        return buildList {
            for (parameter in parametre) {
                add(
                    FerdigstiltePerEnhetTall(
                        dato = dato,
                        parameter = parameter,
                        gruppe = FerdigstiltePerEnhetGruppe.ALLE,
                        antall = hentAntallFraDatabase(
                            dato = dato,
                            ytelser = null,
                            oppgavetype = null,
                            parameter = parameter
                        )
                    )
                )
                for (gruppe in grupper) {
                    add(
                        FerdigstiltePerEnhetTall(
                            dato = dato,
                            parameter = parameter,
                            gruppe = gruppe,
                            antall = hentAntallFraDatabase(
                                dato = dato,
                                ytelser = gruppe.ytelser,
                                parameter = parameter,
                            )
                        )
                    )
                }
                if (parameter is FerdigstiltParameter.Enhet) {
                    // Punsj har ikke helautomatiske behandlinger
                    add(
                        FerdigstiltePerEnhetTall(
                            dato = dato,
                            parameter = parameter,
                            gruppe = FerdigstiltePerEnhetGruppe.PUNSJ,
                            antall = hentAntallFraDatabase(
                                dato = dato,
                                oppgavetype = "k9punsj",
                                parameter = parameter,
                            )
                        )
                    )
                }
            }
        }
    }

    private fun hentAntallFraDatabase(
        dato: LocalDate,
        ytelser: List<FagsakYtelseType>? = null,
        oppgavetype: String? = null,
        parameter: FerdigstiltParameter,
    ): Int {
        val request = QueryRequest(
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    when (parameter) {
                        is FerdigstiltParameter.Enhet -> {
                            add(
                                FeltverdiOppgavefilter(
                                    "K9",
                                    "ferdigstiltEnhet",
                                    EksternFeltverdiOperator.EQUALS,
                                    listOf(parameter.enhet)
                                )
                            )
                        }
                        is FerdigstiltParameter.Helautomatisk -> {
                            add(
                                FeltverdiOppgavefilter(
                                    "K9",
                                    "helautomatiskBehandlet",
                                    EksternFeltverdiOperator.EQUALS,
                                    listOf(true.toString())
                                )
                            )
                        }
                    }
                    if (ytelser != null) {
                        add(
                            FeltverdiOppgavefilter(
                                "K9",
                                "ytelsestype",
                                EksternFeltverdiOperator.IN,
                                ytelser.map { it.kode }
                            )
                        )
                    }
                    if (oppgavetype != null) {
                        add(
                            FeltverdiOppgavefilter(
                                null,
                                "oppgavetype",
                                EksternFeltverdiOperator.EQUALS,
                                listOf(oppgavetype)
                            )
                        )
                    }
                    add(
                        FeltverdiOppgavefilter(
                            null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode)
                        )
                    )
                    add(
                        FeltverdiOppgavefilter(
                            null, "ferdigstiltDato", EksternFeltverdiOperator.EQUALS, listOf(dato.toString())
                        )
                    )
                }
            ),
        )
        return queryService.queryForAntall(request).toInt()
    }
}

