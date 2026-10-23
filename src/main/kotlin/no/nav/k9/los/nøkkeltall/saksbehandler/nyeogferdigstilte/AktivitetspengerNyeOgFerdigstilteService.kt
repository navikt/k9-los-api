package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFeltIder
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.CacheObject
import no.nav.k9.los.nøkkeltall.KodeOgNavn
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.*
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.ung.kodeverk.behandling.BehandlingType
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

class AktivitetspengerNyeOgFerdigstilteService(
    private val queryService: OppgaveQueryService,
) : NyeOgFerdigstilte {
    override val grupper = listOf(
        KodeOgNavn("ALLE", "Alle behandlinger"),
        KodeOgNavn("NAVKONTOR", "Nav-kontor"),
        KodeOgNavn("NAVFORVALTNING", "Nav-forvaltning"),
        KodeOgNavn("KLAGE", "Klager"),
        KodeOgNavn("FEILUTBETALING", "Feilutbetalinger"),
    )
    private val cache = Cache<LocalDate, Map<String, NyeOgFerdigstilteResponse>>(1)
    private val log = LoggerFactory.getLogger(AktivitetspengerNyeOgFerdigstilteService::class.java)
    private val datoformat = DateTimeFormatter.ofPattern("dd.MM")

    override fun hentCachetVerdi(gruppe: String): NyeOgFerdigstilteResponse {
        require(grupper.any { it.kode == gruppe }) { "Gruppen finnes ikke for aktivitetspenger" }
        val dato = LocalDate.now()
        return cache.get(dato)?.value?.getValue(gruppe) ?: NyeOgFerdigstilteResponse(
            oppdatertTidspunkt = null,
            grupper = grupper,
            kolonner = dato.sisteSjuDager().map { it.format(datoformat) },
        )
    }

    override fun oppdaterCache(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val dato = LocalDate.now()
            val tidBrukt = measureTime {
                val verdi = hentFraDatabase(dato)
                cache.set(dato, CacheObject(verdi, dato.plusDays(1).atStartOfDay()))
            }
            log.info("Oppdaterte nye og ferdigstilte for aktivitetspenger på $tidBrukt")
        }
    }

    private fun LocalDate.sisteSjuDager(): List<LocalDate> = minusDays(6).datesUntil(plusDays(1)).toList()

    private data class TelleRad(val oppgavetype: String?, val behandlingstype: String?, val antall: Long) {
        fun matcher(gruppe: String): Boolean = when (gruppe) {
            "ALLE" -> true
            "NAVKONTOR" -> oppgavetype == AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
            "NAVFORVALTNING" -> oppgavetype == AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode
            "KLAGE" -> behandlingstype in setOf(BehandlingType.KLAGE.kode, BehandlingType.ANKE.kode)
            "FEILUTBETALING" -> behandlingstype in setOf(BehandlingType.TILBAKEKREVING.kode, BehandlingType.REVURDERING_TILBAKEKREVING.kode)
            else -> error("Ukjent gruppe: $gruppe")
        }
    }

    private fun hentGrupperte(vararg filtre: Oppgavefilter): List<TelleRad> = queryService.query(
        QueryRequest(
            Områder.AKTIVITETSPENGER,
            OppgaveQuery(
                filtere = filtre.toList(),
                select = listOf(
                    EnkelSelectFelt(null, "oppgavetype"),
                    EnkelSelectFelt(Områder.AKTIVITETSPENGER, AktivitetspengerFeltIder.Behandling.TYPEKODE),
                    AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
                ),
            ),
        )
    ).map { rad ->
        TelleRad(
            oppgavetype = rad.feltverdier.find { it.kode == "oppgavetype" }?.verdi?.toString(),
            behandlingstype = rad.feltverdier.find { it.kode == AktivitetspengerFeltIder.Behandling.TYPEKODE }?.verdi?.toString(),
            antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi) as Long,
        )
    }

    private fun hentFraDatabase(dato: LocalDate): Map<String, NyeOgFerdigstilteResponse> {
        val datoer = dato.sisteSjuDager()
        val nye = datoer.map { dag ->
            hentGrupperte(FeltverdiOppgavefilter(
                Områder.AKTIVITETSPENGER, AktivitetspengerFeltIder.Sak.MOTTATT_DATO,
                EksternFeltverdiOperator.EQUALS, listOf(dag.toString()),
            ))
        }
        val ferdigstilte = datoer.map { dag ->
            hentGrupperte(
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode)),
                FeltverdiOppgavefilter(null, "ferdigstiltDato", EksternFeltverdiOperator.EQUALS, listOf(dag.toString())),
            )
        }
        val oppdatert = LocalDateTime.now()
        return grupper.associate { gruppe ->
            fun List<List<TelleRad>>.antallPerDag() = map { rader ->
                Math.toIntExact(rader.filter { it.matcher(gruppe.kode) }.sumOf { it.antall })
            }
            gruppe.kode to NyeOgFerdigstilteResponse(
                oppdatertTidspunkt = oppdatert,
                grupper = grupper,
                kolonner = datoer.map { it.format(datoformat) },
                serier = listOf(
                    NyeOgFerdigstilteSerie("Nye", nye.antallPerDag()),
                    NyeOgFerdigstilteSerie("Ferdigstilte", ferdigstilte.antallPerDag()),
                ),
            )
        }
    }
}
