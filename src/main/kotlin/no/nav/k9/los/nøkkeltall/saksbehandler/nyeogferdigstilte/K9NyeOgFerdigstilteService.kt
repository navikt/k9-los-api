package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.CacheObject
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9FagsakYtelseType
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.dto.query.Oppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

class K9NyeOgFerdigstilteService(
    private val queryService: OppgaveQueryService
) {
    private var oppdatertTidspunkt: LocalDateTime? = null
    private val cache = Cache<NyeOgFerdigstilteGruppe, List<NyeOgFerdigstilteSerie>>(null)
    private val log: Logger = LoggerFactory.getLogger(K9NyeOgFerdigstilteService::class.java)

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
                                hentEtterMottattDatoFraDatabase(dato, gruppe)
                            }
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

    private fun hentEtterMottattDatoFraDatabase(
        dato: LocalDate, gruppe: NyeOgFerdigstilteGruppe
    ): Int {
        val request = QueryRequest(
            område = Områder.K9,
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    leggTilKriterier(gruppe)
                    add(
                        FeltverdiOppgavefilter(
                            Områder.K9, "mottattDato", EksternFeltverdiOperator.EQUALS, listOf(dato.toString())
                        )
                    )
                })
        )
        return queryService.queryForAntall(request).toInt()
    }

    private fun hentFerdigstilteFraDatabase(dato: LocalDate, gruppe: NyeOgFerdigstilteGruppe): Int {
        val request = QueryRequest(
            område = Områder.K9,
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
                        Områder.K9,
                        "ytelsestype",
                        EksternFeltverdiOperator.EQUALS,
                        listOf(K9FagsakYtelseType.OMSORGSPENGER.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.OMSORGSDAGER -> {
                add(
                    FeltverdiOppgavefilter(
                        Områder.K9,
                        "ytelsestype",
                        EksternFeltverdiOperator.IN,
                        listOf(
                            K9FagsakYtelseType.OMSORGSDAGER.kode,
                            K9FagsakYtelseType.OMSORGSPENGER_MA.kode,
                            K9FagsakYtelseType.OMSORGSPENGER_KS.kode,
                            K9FagsakYtelseType.OMSORGSPENGER_AO.kode
                        )
                    )
                )
            }

            NyeOgFerdigstilteGruppe.OPPLÆRINGSPENGER -> {
                add(
                    FeltverdiOppgavefilter(
                        Områder.K9,
                        "ytelsestype",
                        EksternFeltverdiOperator.EQUALS,
                        listOf(K9FagsakYtelseType.OLP.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.PLEIEPENGER_SYKT_BARN -> {
                add(
                    FeltverdiOppgavefilter(
                        Områder.K9,
                        "ytelsestype",
                        EksternFeltverdiOperator.EQUALS,
                        listOf(K9FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.PPN -> {
                add(
                    FeltverdiOppgavefilter(
                        Områder.K9, "ytelsestype", EksternFeltverdiOperator.EQUALS, listOf(K9FagsakYtelseType.PPN.kode)
                    )
                )
            }

            NyeOgFerdigstilteGruppe.PUNSJ -> {
                add(
                    FeltverdiOppgavefilter(
                        null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9punsj")
                    )
                )
            }
        }
    }
}

