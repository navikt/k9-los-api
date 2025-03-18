package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

class FerdigstiltePerEnhetService(
    private val enheter: List<String>,
    private val queryService: OppgaveQueryService
) {
    private var oppdatertTidspunkt: LocalDateTime? = null
    private val cache = Cache<LocalDate, List<FerdigstiltePerEnhetTall>>(null)
    private val log: Logger = LoggerFactory.getLogger(FerdigstiltePerEnhetService::class.java)

    private val fagsakytelser = listOf(
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.PPN,
        FagsakYtelseType.OMSORGSDAGER,
        FagsakYtelseType.OMSORGSPENGER
    )

    fun hentCachetVerdi(gruppe: FerdigstiltePerEnhetGruppe, uker: Int): FerdigstiltePerEnhetResponse {
        require(uker == 2 || uker == 4) { "Uker må være 2 eller 4" }
        cache.removeExpiredObjects(LocalDateTime.now())

        val idag = LocalDate.now()
        val datoer = idag.minusDays(7L * uker).datesUntil(idag).toList()

        return FerdigstiltePerEnhetResponse(
            oppdatertTidspunkt = oppdatertTidspunkt,
            kolonner = datoer.map { it.format(DateTimeFormatter.ofPattern("dd.MM")) },
            serier = enheter.map { enhet ->
                FerdigstiltePerEnhetSerie(
                    navn = enhet,
                    data = datoer.map { dato ->
                        cache.get(dato, LocalDateTime.now())?.value?.find { it.enhet == enhet && it.gruppe == gruppe }?.antall
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
            val datoer = idag.minusDays(28).datesUntil(idag)

            var antallDagerHenter = 0
            val tidBruktPåOppdatering = measureTime {
                datoer.forEach { dato ->
                    if (!cache.containsKey(dato, LocalDateTime.now())) {
                        cache.set(dato, CacheObject(hentFraDatabase(dato), dato.plusDays(29).atStartOfDay()))
                        antallDagerHenter++
                    }
                }
                oppdatertTidspunkt = LocalDateTime.now()
            }
            log.info("Oppdaterte $antallDagerHenter datoer for ferdigstilte per enhet på $tidBruktPåOppdatering")
        }
    }

    private fun hentFraDatabase(dato: LocalDate): List<FerdigstiltePerEnhetTall> {
        return buildList {
            for (enhet in enheter) {
                add(
                    FerdigstiltePerEnhetTall(
                        dato = dato,
                        enhet = enhet,
                        gruppe = FerdigstiltePerEnhetGruppe.ALLE,
                        antall = hentAntallFraDatabase(
                            dato = dato,
                            fagsakYtelseType = null,
                            oppgavetype = null,
                            enhet = enhet
                        )
                    )
                )
                for (ytelse in fagsakytelser) {
                    add(
                        FerdigstiltePerEnhetTall(
                            dato = dato,
                            enhet = enhet,
                            gruppe = FerdigstiltePerEnhetGruppe.fraFagsakYtelse(ytelse),
                            antall = hentAntallFraDatabase(
                                dato = dato,
                                fagsakYtelseType = ytelse,
                                oppgavetype = null,
                                enhet = enhet
                            )
                        )
                    )
                }
                add(
                    FerdigstiltePerEnhetTall(
                        dato = dato,
                        enhet = enhet,
                        gruppe = FerdigstiltePerEnhetGruppe.PUNSJ,
                        antall = hentAntallFraDatabase(
                            dato = dato,
                            fagsakYtelseType = null,
                            oppgavetype = "k9punsj",
                            enhet = enhet
                        )
                    )
                )
            }
        }
    }

    private fun hentAntallFraDatabase(
        dato: LocalDate,
        fagsakYtelseType: FagsakYtelseType? = null,
        oppgavetype: String? = null,
        enhet: String
    ): Int {
        val request = QueryRequest(
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    add(
                        FeltverdiOppgavefilter(
                            "K9",
                            "ferdigstiltEnhet",
                            FeltverdiOperator.EQUALS.name,
                            listOf(enhet)
                        )
                    )
                    if (fagsakYtelseType != null) {
                        add(
                            FeltverdiOppgavefilter(
                                "K9",
                                "ytelsestype",
                                FeltverdiOperator.EQUALS.name,
                                listOf(fagsakYtelseType.kode)
                            )
                        )
                    }
                    if (oppgavetype != null) {
                        add(
                            FeltverdiOppgavefilter(
                                "K9",
                                "oppgavetype",
                                FeltverdiOperator.EQUALS.name,
                                listOf(oppgavetype)
                            )
                        )
                    }
                    add(
                        FeltverdiOppgavefilter(
                            "K9", "ferdigstiltTidspunkt", FeltverdiOperator.EQUALS.name, listOf(dato.toString())
                        )
                    )
                }
            ),
        )
        return queryService.queryForAntall(request).toInt()
    }
}

