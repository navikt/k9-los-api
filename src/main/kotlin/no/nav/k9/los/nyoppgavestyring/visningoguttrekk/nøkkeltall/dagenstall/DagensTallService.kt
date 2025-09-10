package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.measureTime


class DagensTallService(
    private val queryService: OppgaveQueryService
) {
    private val cache = Cache<LocalDate, DagensTallResponse>(null)
    private val log: Logger = LoggerFactory.getLogger(DagensTallService::class.java)

    companion object {
        val omsorgspenger = FeltverdiOppgavefilter(
            "K9",
            "ytelsestype",
            EksternFeltverdiOperator.IN,
            listOf(FagsakYtelseType.OMSORGSPENGER.kode)
        )

        val omsorgsdager = FeltverdiOppgavefilter(
            "K9",
            "ytelsestype",
            EksternFeltverdiOperator.IN,
            listOf(FagsakYtelseType.OMSORGSDAGER, FagsakYtelseType.OMSORGSPENGER_KS, FagsakYtelseType.OMSORGSPENGER_AO, FagsakYtelseType.OMSORGSPENGER_MA).map { it.kode }
        )

        val opplæringspenger = FeltverdiOppgavefilter(
            "K9",
            "ytelsestype",
            EksternFeltverdiOperator.IN,
            listOf(FagsakYtelseType.OLP.kode)
        )

        val psb = FeltverdiOppgavefilter(
            "K9",
            "ytelsestype",
            EksternFeltverdiOperator.IN,
            listOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode)
        )

        val ppn = FeltverdiOppgavefilter(
            "K9",
            "ytelsestype",
            EksternFeltverdiOperator.IN,
            listOf(FagsakYtelseType.PPN.kode)
        )

        val mottattDato = { dato: LocalDate ->
            FeltverdiOppgavefilter(
                "K9",
                "mottattDato",
                EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                listOf(dato.toString())
            )
        }

        val ferdigstiltDato = { dato: LocalDate ->
            FeltverdiOppgavefilter(
                "K9",
                "ferdigstiltDato",
                EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                listOf(dato.toString())
            )
        }

        val lukket = FeltverdiOppgavefilter(
            null,
            "oppgavestatus",
            EksternFeltverdiOperator.EQUALS,
            listOf(Oppgavestatus.LUKKET.kode)
        )

        val åpenVenterUavklart = FeltverdiOppgavefilter(
            null,
            "oppgavestatus",
            EksternFeltverdiOperator.IN,
            listOf(
                Oppgavestatus.AAPEN.kode,
                Oppgavestatus.VENTER.kode,
                Oppgavestatus.UAVKLART.kode
            )
        )

        val førstegang = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(BehandlingType.FORSTEGANGSSOKNAD.kode)
        )

        val revurdering = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.IN,
            listOf(BehandlingType.REVURDERING.kode, BehandlingType.REVURDERING_TILBAKEKREVING.kode)
        )

        val klage = FeltverdiOppgavefilter(
            null,
            "oppgavetype",
            EksternFeltverdiOperator.EQUALS,
            listOf("k9klage")
        )

        val punsj = FeltverdiOppgavefilter(
            null,
            "oppgavetype",
            EksternFeltverdiOperator.EQUALS,
            listOf("k9punsj")
        )

        val innsyn = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(BehandlingType.INNSYN.kode)
        )

        val feilutbetaling = FeltverdiOppgavefilter(
            null,
            "oppgavetype",
            EksternFeltverdiOperator.EQUALS,
            listOf("k9tilbake")
        )

        val unntaksbehandling = FeltverdiOppgavefilter(
            "K9",
            "behandlingTypekode",
            EksternFeltverdiOperator.EQUALS,
            listOf(BehandlingType.UNNTAKSBEHANDLING.kode)
        )

        val helautomatisk = FeltverdiOppgavefilter(
            "K9",
            "helautomatiskBehandlet",
            EksternFeltverdiOperator.EQUALS,
            listOf(true.toString())
        )

        val ikkeHelautomatisk = FeltverdiOppgavefilter(
            "K9",
            "helautomatiskBehandlet",
            EksternFeltverdiOperator.EQUALS,
            listOf(false.toString())
        )
    }

    fun hentCachetVerdi(): DagensTallResponse {
        return cache.get(LocalDate.now())?.value ?: DagensTallResponse(null, emptyList(), emptyList(), emptyList())
    }

    fun oppdaterCache(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            cache.removeExpiredObjects(LocalDateTime.now())
            val tidBruktPåOppdatering = measureTime {
                val dagensTall = hentFraDatabase()
                cache.set(LocalDate.now(), CacheObject(dagensTall, LocalDateTime.now().plusDays(1)))
            }
            log.info("Oppdaterte dagens tall på $tidBruktPåOppdatering")
        }
    }

    private fun antall(vararg filtere: FeltverdiOppgavefilter?): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = filtere.toList().filterNotNull()
                ),
            )
        )
    }

    private fun hentFraDatabase(): DagensTallResponse {
        val iDag = LocalDate.now()
        val enUkeSiden = iDag.minusWeeks(1)
        val toUkerSiden = iDag.minusWeeks(2)
        val fireUkerSiden = iDag.minusWeeks(4)

        val tall = DagensTallHovedgruppe.entries.flatMap { hovedgruppe ->
            val ytelse = when (hovedgruppe) {
                DagensTallHovedgruppe.ALLE -> null
                DagensTallHovedgruppe.OMSORGSPENGER -> omsorgspenger
                DagensTallHovedgruppe.OMSORGSDAGER -> omsorgsdager
                DagensTallHovedgruppe.OPPLÆRINGSPENGER -> opplæringspenger
                DagensTallHovedgruppe.PLEIEPENGER_SYKT_BARN -> psb
                DagensTallHovedgruppe.PPN -> ppn
            }

            DagensTallUndergruppe.entries.map { undergruppe ->
                val behandlingstype = when (undergruppe) {
                    DagensTallUndergruppe.TOTALT -> null
                    DagensTallUndergruppe.FØRSTEGANG -> førstegang
                    DagensTallUndergruppe.REVURDERING -> revurdering
                    DagensTallUndergruppe.KLAGE -> klage
                    DagensTallUndergruppe.PUNSJ -> punsj
                    DagensTallUndergruppe.FEILUTBETALING -> feilutbetaling
                    DagensTallUndergruppe.UNNTAKSBEHANDLING -> unntaksbehandling
                }

                DagensTallDto(
                    hovedgruppe = hovedgruppe,
                    undergruppe = undergruppe,

                    nyeIDag = antall(åpenVenterUavklart, mottattDato(iDag), ytelse, behandlingstype)
                            + antall(lukket, ferdigstiltDato(iDag), mottattDato(iDag), ytelse, behandlingstype),
                    ferdigstilteIDag = antall(lukket, ferdigstiltDato(iDag), ytelse, behandlingstype, ikkeHelautomatisk),
                    ferdigstilteHelautomatiskIDag = antall(lukket, ferdigstiltDato(iDag), ytelse, behandlingstype, helautomatisk),

                    nyeSiste7Dager = antall(åpenVenterUavklart, mottattDato(enUkeSiden), ytelse, behandlingstype)
                                   + antall(lukket, ferdigstiltDato(enUkeSiden), mottattDato(enUkeSiden), ytelse, behandlingstype),
                    ferdigstilteSiste7Dager = antall(lukket, ferdigstiltDato(enUkeSiden), ytelse, behandlingstype, ikkeHelautomatisk),
                    ferdigstilteHelautomatiskSiste7Dager = antall(lukket, ferdigstiltDato(enUkeSiden), ytelse, behandlingstype, helautomatisk),

                    nyeSiste2Uker = antall(åpenVenterUavklart, mottattDato(toUkerSiden), ytelse, behandlingstype)
                                   + antall(lukket, ferdigstiltDato(toUkerSiden), mottattDato(toUkerSiden), ytelse, behandlingstype),
                    ferdigstilteSiste2Uker = antall(lukket, ferdigstiltDato(toUkerSiden), ytelse, behandlingstype, ikkeHelautomatisk),
                    ferdigstilteHelautomatiskSiste2Uker = antall(lukket, ferdigstiltDato(toUkerSiden), ytelse, behandlingstype, helautomatisk),

                    nyeSiste4Uker = antall(åpenVenterUavklart, mottattDato(fireUkerSiden), ytelse, behandlingstype)
                                    + antall(lukket, ferdigstiltDato(fireUkerSiden), mottattDato(fireUkerSiden), ytelse, behandlingstype),
                    ferdigstilteSiste4Uker = antall(lukket, ferdigstiltDato(fireUkerSiden), ytelse, behandlingstype, ikkeHelautomatisk),
                    ferdigstilteHelautomatiskSiste4Uker = antall(lukket, ferdigstiltDato(fireUkerSiden), ytelse, behandlingstype, ikkeHelautomatisk),
                )
            }
        }

        return DagensTallResponse(
            oppdatertTidspunkt = LocalDateTime.now(),
            hovedgrupper = DagensTallHovedgruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            undergrupper = DagensTallUndergruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            tall = tall
        )
    }
}