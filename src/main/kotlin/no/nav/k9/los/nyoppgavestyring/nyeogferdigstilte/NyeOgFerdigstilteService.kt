package no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte

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
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

class NyeOgFerdigstilteService(
    private val queryService: OppgaveQueryService
) {
    private var oppdatertTidspunkt: LocalDateTime? = null
    private val cache = Cache<NyeOgFerdigstilteGruppe, List<NyeOgFerdigstilteSerie>>(null)
    private val log: Logger = LoggerFactory.getLogger(NyeOgFerdigstilteService::class.java)

    fun hentCachetVerdi(gruppe: NyeOgFerdigstilteGruppe): NyeOgFerdigstilteResponse {
        cache.removeExpiredObjects(LocalDateTime.now())

        val idag = LocalDate.now()
        val datoer = idag.listeMed7DagerBakover()

        val serier = cache.get(gruppe)?.value ?: emptyList()

        return NyeOgFerdigstilteResponse(
            oppdatertTidspunkt = oppdatertTidspunkt,
            kolonner = datoer.map { it.format(DateTimeFormatter.ofPattern("dd.MM")) },
            serier = serier
        )
    }

    fun oppdaterCache(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            cache.removeExpiredObjects(LocalDateTime.now())

            val idag = LocalDate.now()
            val datoer = idag.listeMed7DagerBakover()

            val tidBruktPåOppdatering = measureTime {
                NyeOgFerdigstilteGruppe.entries.forEach { gruppe ->
                    val serierForGruppe = listOf(
                        NyeOgFerdigstilteSerie(
                            "Nye",
                            datoer.map { dato ->
                                hentNyeÅpneVenterFraDatabase(dato, gruppe)
                                + hentNyeLukkedeFraDatabase(dato, gruppe) }
                        ),
                        NyeOgFerdigstilteSerie(
                            "Ferdigstilte",
                            datoer.map { dato -> hentFerdigstilteFraDatabase(dato, gruppe) }
                        )
                    )
                    cache.set(gruppe, CacheObject(value = serierForGruppe, expire = LocalDateTime.now().plusDays(1)))
                }
                oppdatertTidspunkt = LocalDateTime.now()
            }
            log.info("Oppdaterte for nye og ferdigstilte på $tidBruktPåOppdatering")
        }
    }

    private fun LocalDate.listeMed7DagerBakover(): List<LocalDate> =
        this.minusDays(6).datesUntil(this.plusDays(1)).toList()

    private fun hentNyeÅpneVenterFraDatabase(
        dato: LocalDate, gruppe: NyeOgFerdigstilteGruppe
    ): Int {
        val request = QueryRequest(
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    leggTilKriterier(gruppe)
                    add(
                        FeltverdiOppgavefilter(
                            "K9", "mottattDato", EksternFeltverdiOperator.EQUALS, listOf(dato.toString())
                        )
                    )
                    add(
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavestatus",
                            EksternFeltverdiOperator.IN,
                            listOf(Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode)
                        )
                    )
                })
        )
        return queryService.queryForAntall(request).toInt()
    }

    private fun hentNyeLukkedeFraDatabase(
        dato: LocalDate, gruppe: NyeOgFerdigstilteGruppe
    ): Int {
        val request = QueryRequest(
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    leggTilKriterier(gruppe)
                    add(
                        FeltverdiOppgavefilter(
                            "K9", "mottattDato", EksternFeltverdiOperator.EQUALS, listOf(dato.toString())
                        )
                    )
                    add(
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavestatus",
                            EksternFeltverdiOperator.IN,
                            listOf(Oppgavestatus.LUKKET.kode)
                        )
                    )
                    add(
                        FeltverdiOppgavefilter(
                            null, "ferdigstiltDato", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(dato.toString())
                        )
                    )
                })
        )
        return queryService.queryForAntall(request).toInt()
    }


    private fun hentFerdigstilteFraDatabase(dato: LocalDate, gruppe: NyeOgFerdigstilteGruppe): Int {
        val request = QueryRequest(
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    leggTilKriterier(gruppe)
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
                })
        )
        return queryService.queryForAntall(request).toInt()
    }

    private fun MutableList<Oppgavefilter>.leggTilKriterier(gruppe: NyeOgFerdigstilteGruppe) {
        when (gruppe) {
            NyeOgFerdigstilteGruppe.ALLE -> {}
            NyeOgFerdigstilteGruppe.OMSORGSPENGER -> {
                add(
                    FeltverdiOppgavefilter(
                        "K9",
                        "ytelsestype",
                        EksternFeltverdiOperator.EQUALS,
                        listOf(FagsakYtelseType.OMSORGSPENGER.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.OMSORGSDAGER -> {
                add(
                    FeltverdiOppgavefilter(
                        "K9", "ytelsestype", EksternFeltverdiOperator.EQUALS, listOf(FagsakYtelseType.OMSORGSDAGER.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.PLEIEPENGER_SYKT_BARN -> {
                add(
                    FeltverdiOppgavefilter(
                        "K9",
                        "ytelsestype",
                        EksternFeltverdiOperator.EQUALS,
                        listOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.PPN -> {
                add(
                    FeltverdiOppgavefilter(
                        "K9", "ytelsestype", EksternFeltverdiOperator.EQUALS, listOf(FagsakYtelseType.PPN.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.PUNSJ -> {
                add(
                    FeltverdiOppgavefilter(
                        "K9", "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9punsj")
                    )
                )
            }
        }
    }
}

