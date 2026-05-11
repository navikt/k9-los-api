package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
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
            null, "personbeskyttelse", EksternFeltverdiOperator.EQUALS,
            listOf(PersonBeskyttelseType.KODE6.kode)
        )

        val ikkeKode6 = FeltverdiOppgavefilter(
            null, "personbeskyttelse", EksternFeltverdiOperator.EQUALS,
            listOf(PersonBeskyttelseType.UTEN_KODE6.kode)
        )

        val åpenVenterUavklart = FeltverdiOppgavefilter(
            område = null, kode = "oppgavestatus", operator = EksternFeltverdiOperator.IN,
            verdi = listOf(Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode, Oppgavestatus.UAVKLART.kode)
        )

        val punsj = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9punsj"))
        val ikkePunsj = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.NOT_EQUALS, listOf("k9punsj"))
        val førstegang = FeltverdiOppgavefilter("K9", "behandlingTypekode", EksternFeltverdiOperator.EQUALS, listOf(BehandlingType.FORSTEGANGSSOKNAD.kode))
        val klage = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9klage"))
        val revurdering = FeltverdiOppgavefilter("K9", "behandlingTypekode", EksternFeltverdiOperator.IN, listOf(BehandlingType.REVURDERING.kode, BehandlingType.REVURDERING_TILBAKEKREVING.kode))
        val feilutbetaling = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9tilbake"))
    }

    /**
     * Kjører én GROUP BY-query på oppgavestatus og returnerer antall per status.
     * Erstatter 3-4 separate queryForAntall-kall per statuskort.
     */
    private fun antallPerStatus(vararg filtere: Oppgavefilter): Map<String, Long> {
        val query = OppgaveQuery(
            filtere = listOf(åpenVenterUavklart) + filtere.toList(),
            select = listOf(
                EnkelSelectFelt(null, "oppgavestatus"),
                AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
            ),
        )
        val resultat = queryService.query(QueryRequest(query))

        return resultat.associate { rad ->
            val status = rad.feltverdier.first().verdi?.toString() ?: ""
            val antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi) as Long
            status to antall
        }
    }

    /**
     * Bygger et statuskort fra grupperte antall. Kildiespørringer bevares per linje
     * slik at frontend kan drille ned.
     */
    private fun byggStatuskort(
        gruppe: StatusGruppe,
        personbeskyttelse: Oppgavefilter,
        vararg gruppefiltre: Oppgavefilter
    ): StatuskortDto {
        val statusAntall = antallPerStatus(personbeskyttelse, *gruppefiltre)

        val åpne = statusAntall[Oppgavestatus.AAPEN.kode] ?: 0L
        val ventende = statusAntall[Oppgavestatus.VENTER.kode] ?: 0L
        val uavklarte = statusAntall[Oppgavestatus.UAVKLART.kode] ?: 0L
        val totalt = åpne + ventende + uavklarte

        val alleFiltre = gruppefiltre.toList()
        fun kildeQuery(vararg ekstraFiltre: Oppgavefilter) =
            OppgaveQuery((listOf(personbeskyttelse) + alleFiltre + ekstraFiltre.toList()))

        val åpenFilter = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.AAPEN.kode))
        val venterFilter = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.VENTER.kode))
        val uavklartFilter = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.UAVKLART.kode))

        return StatuskortDto(
            tittel = KodeOgNavn(gruppe.name, gruppe.tekst),
            topplinje = StatuslinjeDto("åpne", åpne, kildeQuery(åpenFilter)),
            linjer = listOf(
                StatuslinjeDto("venter", ventende, kildeQuery(venterFilter)),
                StatuslinjeDto("uavklart", uavklarte, kildeQuery(uavklartFilter)),
            ),
            bunnlinje = StatuslinjeDto("totalt", totalt, kildeQuery(åpenVenterUavklart)),
        )
    }

    /**
     * KLAGE har ekstra dimensjon: venter-Kabal vs venter-annet.
     * Kjører en ekstra GROUP BY på aktivÅrsak for venter-oppgavene.
     */
    private fun byggKlageStatuskort(personbeskyttelse: Oppgavefilter): StatuskortDto {
        val statusAntall = antallPerStatus(personbeskyttelse, klage)

        val åpne = statusAntall[Oppgavestatus.AAPEN.kode] ?: 0L
        val uavklarte = statusAntall[Oppgavestatus.UAVKLART.kode] ?: 0L
        val totalt = åpne + (statusAntall[Oppgavestatus.VENTER.kode] ?: 0L) + uavklarte

        // Ekstra query for venteårsak-split innenfor venter-klage
        val venterKabalQuery = OppgaveQuery(
            filtere = listOf(
                personbeskyttelse, klage,
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.VENTER.kode)),
                FeltverdiOppgavefilter("K9", "aktivVenteårsak", EksternFeltverdiOperator.EQUALS, listOf(Venteårsak.OVERSENDT_KABAL.kode)),
            ),
            select = listOf(AggregertSelectFelt(Aggregeringsfunksjon.ANTALL)),
        )
        val venterKabal = queryService.queryForAntall(QueryRequest(venterKabalQuery))
        val venterAnnet = (statusAntall[Oppgavestatus.VENTER.kode] ?: 0L) - venterKabal

        fun kildeQuery(vararg ekstraFiltre: Oppgavefilter) =
            OppgaveQuery(listOf(personbeskyttelse, klage) + ekstraFiltre.toList())

        val åpenFilter = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.AAPEN.kode))
        val venterFilter = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.VENTER.kode))
        val uavklartFilter = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.UAVKLART.kode))
        val kabalFilter = FeltverdiOppgavefilter("K9", "aktivVenteårsak", EksternFeltverdiOperator.EQUALS, listOf(Venteårsak.OVERSENDT_KABAL.kode))
        val ikkeKabalFilter = FeltverdiOppgavefilter("K9", "aktivVenteårsak", EksternFeltverdiOperator.NOT_EQUALS, listOf(Venteårsak.OVERSENDT_KABAL.kode))

        return StatuskortDto(
            tittel = KodeOgNavn(StatusGruppe.KLAGE.name, StatusGruppe.KLAGE.tekst),
            topplinje = StatuslinjeDto("åpne", åpne, kildeQuery(åpenFilter)),
            linjer = listOf(
                StatuslinjeDto("venter Kabal", venterKabal, kildeQuery(venterFilter, kabalFilter)),
                StatuslinjeDto("venter annet", venterAnnet, kildeQuery(venterFilter, ikkeKabalFilter)),
                StatuslinjeDto("uavklart", uavklarte, kildeQuery(uavklartFilter)),
            ),
            bunnlinje = StatuslinjeDto("totalt", totalt, kildeQuery(åpenVenterUavklart)),
        )
    }

    private fun hentFraDatabase(kode6: Boolean): List<StatuskortDto> {
        val personbeskyttelse = if (kode6) kunKode6 else ikkeKode6
        val tall = StatusGruppe.entries.map { gruppe ->
            when (gruppe) {
                StatusGruppe.BEHANDLINGER -> byggStatuskort(gruppe, personbeskyttelse, ikkePunsj)
                StatusGruppe.FØRSTEGANG -> byggStatuskort(gruppe, personbeskyttelse, førstegang)
                StatusGruppe.REVURDERING -> byggStatuskort(gruppe, personbeskyttelse, revurdering)
                StatusGruppe.FEILUTBETALING -> byggStatuskort(gruppe, personbeskyttelse, feilutbetaling)
                StatusGruppe.KLAGE -> byggKlageStatuskort(personbeskyttelse)
                StatusGruppe.PUNSJ -> byggStatuskort(gruppe, personbeskyttelse, punsj)
            }
        }

        return tall
            .filter { statuskort -> statuskort.bunnlinje.verdi > 0 }
            .map { statuskort -> statuskort.copy(linjer = statuskort.linjer.filter { linje -> linje.verdi > 0 }) }
    }
}
