package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
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
            listOf(no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType.FORSTEGANGSSOKNAD.kode)
        )

        val klage = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType.KLAGE.kode)
        )

        val revurdering = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType.REVURDERING.kode)
        )

        val feilutbetaling = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType.TILBAKE.kode)
        )

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
    }

    private fun antall(vararg filtere: Oppgavefilter): Long {
        return queryService.queryForAntall(QueryRequest(OppgaveQuery(filtere.toList())))
    }

    private fun hentFraDatabase(kode6: Boolean): List<StatusFordelingDto> {
        val personbeskyttelse = if (kode6) kunKode6 else ikkeKode6
        val tall = StatusGruppe.entries.map { gruppe ->
            when (gruppe) {
                StatusGruppe.BEHANDLINGER -> StatusFordelingDto(
                    gruppe,
                    antall(personbeskyttelse, åpenVenterUavklart, ikkePunsj),
                    antall(personbeskyttelse, åpen, ikkePunsj),
                    antall(personbeskyttelse, venter, ikkePunsj),
                    antall(personbeskyttelse, uavklart, ikkePunsj)
                )

                StatusGruppe.FØRSTEGANG -> StatusFordelingDto(
                    gruppe,
                    antall(personbeskyttelse, åpenVenterUavklart, førstegang),
                    antall(personbeskyttelse, åpen, førstegang),
                    antall(personbeskyttelse, venter, førstegang),
                    antall(personbeskyttelse, uavklart, førstegang)
                )

                StatusGruppe.KLAGE -> StatusFordelingDto(
                    gruppe,
                    antall(personbeskyttelse, åpenVenterUavklart, klage),
                    antall(personbeskyttelse, åpen, klage),
                    antall(personbeskyttelse, venter, klage),
                    antall(personbeskyttelse, uavklart, klage)
                )

                StatusGruppe.REVURDERING -> StatusFordelingDto(
                    gruppe,
                    antall(personbeskyttelse, åpenVenterUavklart, revurdering),
                    antall(personbeskyttelse, åpen, revurdering),
                    antall(personbeskyttelse, venter, revurdering),
                    antall(personbeskyttelse, uavklart, revurdering)
                )

                StatusGruppe.FEILUTBETALING -> StatusFordelingDto(
                    gruppe,
                    antall(personbeskyttelse, åpenVenterUavklart, feilutbetaling),
                    antall(personbeskyttelse, åpen, feilutbetaling),
                    antall(personbeskyttelse, venter, feilutbetaling),
                    antall(personbeskyttelse, uavklart, feilutbetaling)
                )

                StatusGruppe.PUNSJ -> StatusFordelingDto(
                    gruppe,
                    antall(personbeskyttelse, åpenVenterUavklart, punsj),
                    antall(personbeskyttelse, åpen, punsj),
                    antall(personbeskyttelse, venter, punsj),
                    antall(personbeskyttelse, uavklart, punsj)
                )
            }
        }
        return tall
    }
}