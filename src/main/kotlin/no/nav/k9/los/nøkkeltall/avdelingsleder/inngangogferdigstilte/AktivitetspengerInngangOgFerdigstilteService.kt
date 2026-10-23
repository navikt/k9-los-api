package no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte

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
import kotlin.time.measureTime

class AktivitetspengerInngangOgFerdigstilteService(
    private val queryService: OppgaveQueryService,
) : InngangOgFerdigstilte {
    private val cache = Cache<LocalDate, InngangOgFerdigstilteResponse>(null)
    private val log = LoggerFactory.getLogger(AktivitetspengerInngangOgFerdigstilteService::class.java)
    private val hovedgrupper = listOf(
        KodeOgNavn("ALLE", "Alle behandlinger"),
        KodeOgNavn("NAVKONTOR", "Nav-kontor"),
        KodeOgNavn("NAVFORVALTNING", "Nav-forvaltning"),
    )
    private val undergrupper = listOf(
        KodeOgNavn("TOTALT", "Totalt"),
        KodeOgNavn("FØRSTEGANG", "Førstegangsbehandlinger"),
        KodeOgNavn("REVURDERING", "Revurderinger"),
        KodeOgNavn("KLAGE", "Klager"),
        KodeOgNavn("FEILUTBETALING", "Feilutbetalinger"),
    )
    private val oppgavetyper = mapOf(
        "NAVKONTOR" to AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode,
        "NAVFORVALTNING" to AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode,
    )
    private val behandlingstyper = mapOf(
        "FØRSTEGANG" to setOf(BehandlingType.FØRSTEGANGSSØKNAD.kode),
        "REVURDERING" to setOf(BehandlingType.REVURDERING.kode),
        "KLAGE" to setOf(BehandlingType.KLAGE.kode, BehandlingType.ANKE.kode),
        "FEILUTBETALING" to setOf(BehandlingType.TILBAKEKREVING.kode, BehandlingType.REVURDERING_TILBAKEKREVING.kode),
    )

    override fun hentCachetVerdi(): InngangOgFerdigstilteResponse =
        cache.get(LocalDate.now())?.value ?: InngangOgFerdigstilteResponse(null, emptyList(), emptyList(), emptyList())

    override fun oppdaterCache(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val dato = LocalDate.now()
            cache.removeExpiredObjects(LocalDateTime.now())
            val tidBrukt = measureTime {
                val verdi = hentFraDatabase(dato)
                cache.set(dato, CacheObject(verdi, dato.plusDays(1).atStartOfDay()))
            }
            log.info("Oppdaterte inngang og ferdigstilte for aktivitetspenger på $tidBrukt")
        }
    }

    private data class TelleRad(val oppgavetype: String?, val behandlingstype: String?, val antall: Long)

    private fun hentGrupperte(filtre: List<Oppgavefilter>): List<TelleRad> {
        val resultat = queryService.query(
            QueryRequest(
                Områder.AKTIVITETSPENGER,
                OppgaveQuery(
                    filtere = filtre,
                    select = listOf(
                        EnkelSelectFelt(null, "oppgavetype"),
                        EnkelSelectFelt(Områder.AKTIVITETSPENGER, AktivitetspengerFeltIder.Behandling.TYPEKODE),
                        AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
                    ),
                ),
            )
        )
        return resultat.map { rad ->
            TelleRad(
                oppgavetype = rad.feltverdier.find { it.kode == "oppgavetype" }?.verdi?.toString(),
                behandlingstype = rad.feltverdier.find { it.kode == AktivitetspengerFeltIder.Behandling.TYPEKODE }?.verdi?.toString(),
                antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi) as Long,
            )
        }
    }

    private fun hentFraDatabase(dato: LocalDate): InngangOgFerdigstilteResponse {
        val perioder = linkedMapOf(
            "idag" to dato,
            "sisteUke" to dato.minusWeeks(1),
            "siste2Uker" to dato.minusWeeks(2),
            "siste4Uker" to dato.minusWeeks(4),
        )
        val inngang = perioder.mapValues { (_, fraDato) ->
            hentGrupperte(listOf(
                FeltverdiOppgavefilter(
                    Områder.AKTIVITETSPENGER, AktivitetspengerFeltIder.Sak.MOTTATT_DATO,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(fraDato.toString()),
                ),
            ))
        }
        val ferdigstilte = perioder.mapValues { (_, fraDato) ->
            hentGrupperte(listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode)),
                FeltverdiOppgavefilter(null, "ferdigstiltDato", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(fraDato.toString())),
            ))
        }

        fun List<TelleRad>.kort(navn: String, hovedgruppe: String, undergruppe: String) = InngangOgFerdigstilteKortDto(
            hovedtall = InngangOgFerdigstilteLinjeDto(
                navn,
                filter { rad ->
                    (hovedgruppe == "ALLE" || rad.oppgavetype == oppgavetyper[hovedgruppe]) &&
                        (undergruppe == "TOTALT" || rad.behandlingstype in behandlingstyper.getValue(undergruppe))
                }.sumOf { it.antall },
            ),
        )

        return InngangOgFerdigstilteResponse(
            oppdatertTidspunkt = LocalDateTime.now(),
            hovedgrupper = hovedgrupper,
            undergrupper = undergrupper,
            tall = hovedgrupper.flatMap { hovedgruppe ->
                undergrupper
                    .filterNot { undergruppe -> hovedgruppe.kode == "NAVKONTOR" && undergruppe.kode == "FEILUTBETALING" }
                    .map { undergruppe ->
                        InngangOgFerdigstilteDto(
                            hovedgruppe.kode,
                            undergruppe.kode,
                            perioder.mapValues { (periode, _) ->
                                inngang.getValue(periode).kort("Inngang", hovedgruppe.kode, undergruppe.kode) to
                                    ferdigstilte.getValue(periode).kort("Ferdigstilt", hovedgruppe.kode, undergruppe.kode)
                            },
                        )
                    }
            },
        )
    }
}
