package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.measureTime

class FerdigstiltPerEnhetService(
    private val queryService: OppgaveQueryService
) {
    private val cache = Cache<LocalDate, FerdigstiltPerEnhetResponse.Suksess>(null)
    private val log: Logger = LoggerFactory.getLogger(FerdigstiltPerEnhetService::class.java)

    private val enheter = listOf("4409", "4432")
    private val fagsakytelser = listOf(
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.PPN,
        FagsakYtelseType.OMSORGSDAGER,
        FagsakYtelseType.OMSORGSPENGER
    )

    fun hentCachetVerdi(): FerdigstiltPerEnhetResponse {
        return cache.get(LocalDate.now())?.value
            ?: FerdigstiltPerEnhetResponse.Feil("Har ikke lastet inn ferdigstilte per enhet ennå")
    }

    fun oppdaterCache(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            cache.removeExpiredObjects(LocalDateTime.now())
            val tidBruktPåOppdatering = measureTime {
                cache.set(
                    LocalDate.now(), CacheObject(
                        value = FerdigstiltPerEnhetResponse.Suksess(
                            oppdatertTidspunkt = LocalDateTime.now(),
                            grupper = PerEnhetGruppe.entries.map { KodeOgNavn(it.name, it.navn) },
                            tall = hentFraDatabase()
                        ), expire = LocalDateTime.now().plusDays(1)
                    )
                )
            }
            log.info("Oppdaterte ferdigstilte per enhet på $tidBruktPåOppdatering")
        }
    }

    private fun hentFraDatabase(): List<FerdigstiltPerEnhetTall> {
        val igår = LocalDate.now().minusDays(1)
        val fireUkerTilbake = igår.minusDays(28).datesUntil(igår)

        return buildList {
            for (dato in fireUkerTilbake) {
                for (enhet in enheter) {
                    add(
                        FerdigstiltPerEnhetTall(
                            dato = dato,
                            enhet = enhet,
                            antall = hentAntallFraDatabase(
                                dato = dato,
                                enhet = enhet,
                                fagsakYtelseType = null,
                                oppgavetype = null
                            ),
                            gruppe = PerEnhetGruppe.ALLE
                        )
                    )
                    for (ytelse in fagsakytelser) {
                        add(
                            FerdigstiltPerEnhetTall(
                                dato = dato,
                                enhet = enhet,
                                antall = hentAntallFraDatabase(
                                    dato = dato,
                                    enhet = enhet,
                                    fagsakYtelseType = ytelse,
                                    oppgavetype = null
                                ),
                                gruppe = PerEnhetGruppe.fraFagsakYtelse(ytelse)
                            )
                        )
                    }
                    add(
                        FerdigstiltPerEnhetTall(
                            dato = dato,
                            enhet = enhet,
                            antall = hentAntallFraDatabase(
                                dato = dato,
                                enhet = enhet,
                                fagsakYtelseType = null,
                                oppgavetype = "k9punsj"
                            ),
                            gruppe = PerEnhetGruppe.PUNSJ
                        )
                    )
                }
            }
        }
    }

    private fun hentAntallFraDatabase(
        dato: LocalDate,
        enhet: String,
        fagsakYtelseType: FagsakYtelseType?,
        oppgavetype: String?
    ): Int {
        val request = QueryRequest(
            oppgaveQuery = OppgaveQuery(
                filtere = buildList {
                    add(
                        FeltverdiOppgavefilter(
                            "K9",
                            "behandlendeEnhet",
                            FeltverdiOperator.EQUALS.name,
                            listOf(enhet)
                        )
                    )
                    if (fagsakYtelseType != null) {
                        add(
                            FeltverdiOppgavefilter(
                                "K9",
                                "ytelsetype",
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
                            "K9",
                            "status",
                            FeltverdiOperator.EQUALS.name,
                            listOf(Oppgavestatus.LUKKET.kode)
                        )
                    )
                    add(
                        FeltverdiOppgavefilter(
                            "K9", "sistEndret", FeltverdiOperator.GREATER_THAN_OR_EQUALS.name, listOf(
                                dato.toString()
                            )
                        )
                    )
                    add(
                        FeltverdiOppgavefilter(
                            "K9",
                            "sistEndret",
                            FeltverdiOperator.LESS_THAN.name,
                            listOf(dato.plusDays(1).toString())
                        )
                    )
                }
            ),
            fraAktiv = false
        )
        return queryService.queryForAntall(request).toInt()
    }
}
