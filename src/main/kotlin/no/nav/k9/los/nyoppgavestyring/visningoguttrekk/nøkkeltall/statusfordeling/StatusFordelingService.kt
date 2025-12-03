package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime
import kotlin.time.toJavaDuration

class StatusFordelingService(val queryService: OppgaveQueryService) {
    private val cache: Cache<Boolean, StatusFordelingResponse> = Cache(2)
    private val cacheLevetid = 5.minutes.toJavaDuration()
    private val log: Logger = LoggerFactory.getLogger(StatusFordelingService::class.java)

    fun hentVerdi(kode6: Boolean): StatusFordelingResponse {
        return cache.hent(kode6, cacheLevetid) { StatusFordelingResponse(LocalDateTime.now(), hentFraDatabase(kode6)) }
    }

    fun oppdaterCache(kode6: Boolean) {
        val tidBruktPåOppdatering = measureTime {
            val tall = hentFraDatabase(kode6)
            cache.set(
                kode6,
                CacheObject(
                    value = StatusFordelingResponse(
                        oppdatertTidspunkt = LocalDateTime.now(),
                        tall = tall
                    ),
                    expire = LocalDateTime.now().plus(cacheLevetid),
                )
            )
        }

        log.info("Oppdaterte statusfordeling på $tidBruktPåOppdatering")
    }

    private companion object {
        val kunKode6 = FeltverdiOppgavefilter(
            null,
            "personbeskyttelse",
            EksternFeltverdiOperator.EQUALS,
            listOf(PersonBeskyttelseType.KODE6.kode)
        )

        val ikkeKode6 = FeltverdiOppgavefilter(
            null,
            "personbeskyttelse",
            EksternFeltverdiOperator.EQUALS,
            listOf(PersonBeskyttelseType.UTEN_KODE6.kode)
        )

        val åpenVenterUavklart = FeltverdiOppgavefilter(
            område = null,
            kode = "oppgavestatus",
            operator = EksternFeltverdiOperator.IN,
            verdi = listOf(
                Oppgavestatus.AAPEN.kode,
                Oppgavestatus.VENTER.kode,
                Oppgavestatus.UAVKLART.kode
            )
        )

        val åpen = FeltverdiOppgavefilter(
            område = null,
            kode = "oppgavestatus",
            operator = EksternFeltverdiOperator.EQUALS,
            verdi = listOf(
                Oppgavestatus.AAPEN.kode,
            )
        )

        val venter = FeltverdiOppgavefilter(
            område = null,
            kode = "oppgavestatus",
            operator = EksternFeltverdiOperator.EQUALS,
            verdi = listOf(
                Oppgavestatus.VENTER.kode,
            )
        )

        val uavklart = FeltverdiOppgavefilter(
            område = null,
            kode = "oppgavestatus",
            operator = EksternFeltverdiOperator.EQUALS,
            verdi = listOf(
                Oppgavestatus.UAVKLART.kode,
            )
        )

        val punsj = FeltverdiOppgavefilter(
            område = null,
            kode = "oppgavetype",
            operator = EksternFeltverdiOperator.EQUALS,
            verdi = listOf("k9punsj")
        )

        val ikkePunsj = FeltverdiOppgavefilter(
            område = null,
            kode = "oppgavetype",
            operator = EksternFeltverdiOperator.NOT_EQUALS,
            verdi = listOf("k9punsj")
        )

        val førstegang = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(BehandlingType.FORSTEGANGSSOKNAD.kode)
        )

        val klage = FeltverdiOppgavefilter(
            null,
            "oppgavetype",
            EksternFeltverdiOperator.EQUALS,
            listOf("k9klage")
        )

        val venterKabal = FeltverdiOppgavefilter(
            område = "K9",
            kode = "aktivVenteårsak",
            operator = EksternFeltverdiOperator.EQUALS,
            listOf(Venteårsak.OVERSENDT_KABAL.kode)
        )

        val venterIkkeKabal = FeltverdiOppgavefilter(
            område = "K9",
            kode = "aktivVenteårsak",
            operator = EksternFeltverdiOperator.NOT_EQUALS,
            listOf(Venteårsak.OVERSENDT_KABAL.kode)
        )

        val revurdering = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.IN,
            listOf(
                BehandlingType.REVURDERING.kode,
                BehandlingType.REVURDERING_TILBAKEKREVING.kode)
        )

        val feilutbetaling = FeltverdiOppgavefilter(
            null,
            "oppgavetype",
            EksternFeltverdiOperator.EQUALS,
            listOf("k9tilbake")
        )

        val unntak = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(BehandlingType.UNNTAKSBEHANDLING.kode)
        )
    }

    private fun antall(
        visningsnavn: String,
        vararg filtere: Oppgavefilter
    ): StatuslinjeDto {
        val query = OppgaveQuery(filtere.toList())
        return StatuslinjeDto(
            visningsnavn,
            queryService.queryForAntall(QueryRequest(query)),
            query
        )
    }

    private fun hentFraDatabase(kode6: Boolean): List<StatuskortDto> {
        val personbeskyttelse = if (kode6) kunKode6 else ikkeKode6
        val tall = StatusGruppe.entries.map { gruppe ->
            when (gruppe) {
                StatusGruppe.BEHANDLINGER -> StatuskortDto(
                    tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
                    topplinje = antall("åpne", personbeskyttelse, åpen, ikkePunsj),
                    linjer =
                        listOf(
                            antall("venter", personbeskyttelse, venter, ikkePunsj),
                            antall("uavklart", personbeskyttelse, uavklart, ikkePunsj),
                        ),
                    bunnlinje = antall("totalt", personbeskyttelse, åpenVenterUavklart, ikkePunsj),
                )

                StatusGruppe.FØRSTEGANG -> StatuskortDto(
                    tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
                    topplinje = antall("åpne", personbeskyttelse, åpen, førstegang),
                    linjer = listOf(
                        antall("venter", personbeskyttelse, venter, førstegang),
                        antall("uavklart", personbeskyttelse, uavklart, førstegang),
                    ),
                    bunnlinje = antall("totalt", personbeskyttelse, åpenVenterUavklart, førstegang)
                )

                StatusGruppe.REVURDERING -> StatuskortDto(
                    tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
                    topplinje = antall("åpne", personbeskyttelse, åpen, revurdering),
                    linjer = listOf(
                        antall("venter", personbeskyttelse, venter, revurdering),
                        antall("uavklart", personbeskyttelse, uavklart, revurdering),
                    ),
                    bunnlinje = antall("totalt", personbeskyttelse, åpenVenterUavklart, revurdering),
                )

                StatusGruppe.FEILUTBETALING -> StatuskortDto(
                    tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
                    topplinje = antall("åpne", personbeskyttelse, åpen, feilutbetaling),
                    linjer = listOf(
                        antall("venter", personbeskyttelse, venter, feilutbetaling),
                        antall("uavklart", personbeskyttelse, uavklart, feilutbetaling),
                    ),
                    bunnlinje = antall("totalt", personbeskyttelse, åpenVenterUavklart, feilutbetaling),
                )

                StatusGruppe.KLAGE -> StatuskortDto(
                    tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
                    topplinje = antall("åpne", personbeskyttelse, åpen, klage),
                    linjer = listOf(
                        antall("venter Kabal", personbeskyttelse, venter, klage, venterKabal),
                        antall("venter annet", personbeskyttelse, venter, klage, venterIkkeKabal),
                        antall("uavklart", personbeskyttelse, uavklart, klage),
                    ),
                    bunnlinje = antall("totalt", personbeskyttelse, åpenVenterUavklart, klage),
                )

                StatusGruppe.PUNSJ -> StatuskortDto(
                    tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
                    topplinje = antall("åpne", personbeskyttelse, åpen, punsj),
                    linjer = listOf(
                        antall("venter", personbeskyttelse, venter, punsj),
                        antall("uavklart", personbeskyttelse, uavklart, punsj),
                    ),
                    bunnlinje = antall("totalt", personbeskyttelse, åpenVenterUavklart, punsj),
                )
            }
        }

        return tall
            .filter { statuskort -> statuskort.bunnlinje.verdi > 0 }
            .map { statuskort -> statuskort.copy(linjer = statuskort.linjer.filter { linje -> linje.verdi > 0 }) }
    }
}