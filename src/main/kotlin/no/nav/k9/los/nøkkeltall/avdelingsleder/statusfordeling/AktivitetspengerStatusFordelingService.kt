package no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling

import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerFeltIder
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.CacheObject
import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nøkkeltall.KodeOgNavn
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.oppgaveuthenting.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.ung.kodeverk.behandling.BehandlingType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime
import kotlin.time.toJavaDuration

class AktivitetspengerStatusFordelingService(
    private val queryService: OppgaveQueryService,
) : StatusFordeling {
    private val cache = Cache<Boolean, StatusFordelingResponse>(2)
    private val cacheLevetid = 5.minutes.toJavaDuration()
    private val log = LoggerFactory.getLogger(AktivitetspengerStatusFordelingService::class.java)

    override fun hentVerdi(kode6: Boolean): StatusFordelingResponse =
        cache.hent(kode6, cacheLevetid) { hentFraDatabase(kode6) }

    override fun oppdaterCache(kode6: Boolean) {
        val tidBrukt = measureTime {
            cache.set(kode6, CacheObject(hentFraDatabase(kode6), LocalDateTime.now().plus(cacheLevetid)))
        }
        log.info("Oppdaterte statusfordeling for aktivitetspenger på $tidBrukt")
    }

    private fun hentFraDatabase(kode6: Boolean): StatusFordelingResponse {
        val personbeskyttelse = FeltverdiOppgavefilter(
            null, "personbeskyttelse", EksternFeltverdiOperator.EQUALS,
            listOf(if (kode6) PersonBeskyttelseType.KODE6.kode else PersonBeskyttelseType.UTEN_KODE6.kode),
        )
        val del1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
        val del2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode
        fun oppgavetype(kode: String) = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.IN, listOf(kode))
        fun behandlingstyper(vararg typer: BehandlingType) = FeltverdiOppgavefilter(
            Områder.AKTIVITETSPENGER, AktivitetspengerFeltIder.Behandling.TYPEKODE,
            EksternFeltverdiOperator.IN, typer.map { it.kode },
        )
        val tall = listOf(
            byggStatuskort(KodeOgNavn("BEHANDLINGER", "Behandlinger"), personbeskyttelse),
            byggStatuskort(KodeOgNavn("DEL1", "Del 1"), personbeskyttelse, oppgavetype(del1)),
            byggStatuskort(KodeOgNavn("DEL2", "Del 2"), personbeskyttelse, oppgavetype(del2)),
            byggStatuskort(
                KodeOgNavn("KLAGE", "Klager"), personbeskyttelse,
                behandlingstyper(BehandlingType.KLAGE, BehandlingType.ANKE),
            ),
            byggStatuskort(
                KodeOgNavn("FEILUTBETALING", "Feilutbetalinger"), personbeskyttelse,
                behandlingstyper(BehandlingType.TILBAKEKREVING, BehandlingType.REVURDERING_TILBAKEKREVING),
            ),
        ).filter { it.bunnlinje.verdi > 0 }
            .map { kort -> kort.copy(linjer = kort.linjer.filter { it.verdi > 0 }) }

        return StatusFordelingResponse(oppdatertTidspunkt = LocalDateTime.now(), tall = tall)
    }

    private fun byggStatuskort(
        tittel: KodeOgNavn,
        personbeskyttelse: FeltverdiOppgavefilter,
        vararg gruppefiltre: FeltverdiOppgavefilter,
    ): StatuskortDto {
        val grunnfiltre = listOf(personbeskyttelse) + gruppefiltre
        val aktiveStatuser = FeltverdiOppgavefilter(
            null, "oppgavestatus", EksternFeltverdiOperator.IN,
            listOf(Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode, Oppgavestatus.UAVKLART.kode),
        )
        val resultat = queryService.query(
            QueryRequest(
                Områder.AKTIVITETSPENGER,
                OppgaveQuery(
                    filtere = grunnfiltre + aktiveStatuser,
                    select = listOf(
                        EnkelSelectFelt(null, "oppgavestatus"),
                        AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
                    ),
                ),
            )
        )
        val antallPerStatus = resultat.associate { rad ->
            val status = checkNotNull(rad.feltverdier.first().verdi).toString()
            val antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi) as Long
            status to antall
        }

        fun statuslinje(navn: String, status: Oppgavestatus) = StatuslinjeDto(
            navn,
            antallPerStatus[status.kode] ?: 0L,
            OppgaveQuery(
                grunnfiltre + FeltverdiOppgavefilter(
                    null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(status.kode),
                )
            ),
        )

        return StatuskortDto(
            tittel = tittel,
            topplinje = statuslinje("åpne", Oppgavestatus.AAPEN),
            linjer = listOf(
                statuslinje("venter", Oppgavestatus.VENTER),
                statuslinje("uavklart", Oppgavestatus.UAVKLART),
            ),
            bunnlinje = StatuslinjeDto("totalt", antallPerStatus.values.sum(), OppgaveQuery(grunnfiltre + aktiveStatuser)),
        )
    }
}
