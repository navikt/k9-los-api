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
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.time.measureTime


class DagensTallService(
    private val queryService: OppgaveQueryService
) {
    private val cache = Cache<LocalDate, DagensTallResponse>(null)
    private val log: Logger = LoggerFactory.getLogger(DagensTallService::class.java)

    companion object {
        val omsorgspenger = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.OMSORGSPENGER.kode))
        val omsorgsdager = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN,
            listOf(FagsakYtelseType.OMSORGSDAGER, FagsakYtelseType.OMSORGSPENGER_KS, FagsakYtelseType.OMSORGSPENGER_AO, FagsakYtelseType.OMSORGSPENGER_MA).map { it.kode })
        val opplæringspenger = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.OLP.kode))
        val psb = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode))
        val ppn = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.PPN.kode))

        val mottattDato = { dato: LocalDate -> FeltverdiOppgavefilter("K9", "mottattDato", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(dato.toString())) }
        val ferdigstiltDato = { dato: LocalDate -> FeltverdiOppgavefilter(null, "ferdigstiltDato", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(dato.toString())) }
        val mottattDatoFør = { dato: LocalDate -> FeltverdiOppgavefilter("K9", "mottattDato", EksternFeltverdiOperator.LESS_THAN, listOf(dato.toString())) }
        val ferdigstiltDatoFør = { dato: LocalDate -> FeltverdiOppgavefilter(null, "ferdigstiltDato", EksternFeltverdiOperator.LESS_THAN, listOf(dato.toString())) }
        val lukket = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode))

        val førstegang = FeltverdiOppgavefilter("K9", "behandlingTypekode", EksternFeltverdiOperator.EQUALS, listOf(BehandlingType.FORSTEGANGSSOKNAD.kode))
        val revurdering = FeltverdiOppgavefilter("K9", "behandlingTypekode", EksternFeltverdiOperator.IN, listOf(BehandlingType.REVURDERING.kode, BehandlingType.REVURDERING_TILBAKEKREVING.kode))
        val klage = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9klage"))
        val punsj = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9punsj"))
        val feilutbetaling = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9tilbake"))
        val unntaksbehandling = FeltverdiOppgavefilter("K9", "behandlingTypekode", EksternFeltverdiOperator.EQUALS, listOf(BehandlingType.UNNTAKSBEHANDLING.kode))
        val helautomatisk = FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", EksternFeltverdiOperator.EQUALS, listOf(true.toString()))
        val ikkeHelautomatisk = FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", EksternFeltverdiOperator.EQUALS, listOf(false.toString()))

        private val hovedgruppeYtelser: Map<DagensTallHovedgruppe, Set<String>?> = mapOf(
            DagensTallHovedgruppe.ALLE to null,
            DagensTallHovedgruppe.OMSORGSPENGER to setOf(FagsakYtelseType.OMSORGSPENGER.kode),
            DagensTallHovedgruppe.OMSORGSDAGER to setOf(FagsakYtelseType.OMSORGSDAGER, FagsakYtelseType.OMSORGSPENGER_KS, FagsakYtelseType.OMSORGSPENGER_AO, FagsakYtelseType.OMSORGSPENGER_MA).map { it.kode }.toSet(),
            DagensTallHovedgruppe.OPPLÆRINGSPENGER to setOf(FagsakYtelseType.OLP.kode),
            DagensTallHovedgruppe.PLEIEPENGER_SYKT_BARN to setOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode),
            DagensTallHovedgruppe.PPN to setOf(FagsakYtelseType.PPN.kode),
        )

        private fun ytelseFilter(hovedgruppe: DagensTallHovedgruppe): FeltverdiOppgavefilter? = when (hovedgruppe) {
            DagensTallHovedgruppe.ALLE -> null
            DagensTallHovedgruppe.OMSORGSPENGER -> omsorgspenger
            DagensTallHovedgruppe.OMSORGSDAGER -> omsorgsdager
            DagensTallHovedgruppe.OPPLÆRINGSPENGER -> opplæringspenger
            DagensTallHovedgruppe.PLEIEPENGER_SYKT_BARN -> psb
            DagensTallHovedgruppe.PPN -> ppn
        }

        private fun behandlingFilter(undergruppe: DagensTallUndergruppe): FeltverdiOppgavefilter? = when (undergruppe) {
            DagensTallUndergruppe.TOTALT -> null
            DagensTallUndergruppe.FØRSTEGANG -> førstegang
            DagensTallUndergruppe.REVURDERING -> revurdering
            DagensTallUndergruppe.KLAGE -> klage
            DagensTallUndergruppe.PUNSJ -> punsj
            DagensTallUndergruppe.FEILUTBETALING -> feilutbetaling
            DagensTallUndergruppe.UNNTAKSBEHANDLING -> unntaksbehandling
        }
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

    /**
     * En rad fra en gruppert query. Brukes til å slå opp antall i minne
     * i stedet for å kjøre én query per kombinasjon.
     */
    private data class TelleRad(
        val ytelsestype: String?,
        val oppgavetype: String?,
        val behandlingTypekode: String?,
        val helautomatisk: String?,
        val antall: Long
    )

    private fun hentGrupperte(filtere: List<Oppgavefilter>, medHelautomatisk: Boolean): List<TelleRad> {
        val selectFelter = buildList {
            add(EnkelSelectFelt("K9", "ytelsestype"))
            add(EnkelSelectFelt(null, "oppgavetype"))
            add(EnkelSelectFelt("K9", "behandlingTypekode"))
            if (medHelautomatisk) add(EnkelSelectFelt("K9", "helautomatiskBehandlet"))
            add(AggregertSelectFelt(Aggregeringsfunksjon.ANTALL))
        }
        val query = OppgaveQuery(filtere = filtere, select = selectFelter)
        val resultat = queryService.query(QueryRequest(query))
        if (resultat !is OppgaveQueryResultat.GruppertResultat) return emptyList()

        return resultat.rader.map { rad ->
            TelleRad(
                ytelsestype = rad.grupperingsverdier.find { it.kode == "ytelsestype" }?.verdi?.toString(),
                oppgavetype = rad.grupperingsverdier.find { it.kode == "oppgavetype" }?.verdi?.toString(),
                behandlingTypekode = rad.grupperingsverdier.find { it.kode == "behandlingTypekode" }?.verdi?.toString(),
                helautomatisk = if (medHelautomatisk) rad.grupperingsverdier.find { it.kode == "helautomatiskBehandlet" }?.verdi?.toString() else null,
                antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi).toLong()
            )
        }
    }

    /**
     * Teller opp fra forhåndshentede grupperte rader med in-memory filtrering.
     */
    private fun List<TelleRad>.tell(
        ytelseKoder: Set<String>? = null,
        undergruppe: DagensTallUndergruppe = DagensTallUndergruppe.TOTALT,
        helautomatisk: Boolean? = null
    ): Long {
        return this.filter { rad ->
            (ytelseKoder == null || rad.ytelsestype in ytelseKoder) &&
            matcherUndergruppe(rad, undergruppe) &&
            (helautomatisk == null || rad.helautomatisk == helautomatisk.toString())
        }.sumOf { it.antall }
    }

    private fun matcherUndergruppe(rad: TelleRad, undergruppe: DagensTallUndergruppe): Boolean = when (undergruppe) {
        DagensTallUndergruppe.TOTALT -> true
        DagensTallUndergruppe.FØRSTEGANG -> rad.behandlingTypekode == BehandlingType.FORSTEGANGSSOKNAD.kode
        DagensTallUndergruppe.REVURDERING -> rad.behandlingTypekode in setOf(BehandlingType.REVURDERING.kode, BehandlingType.REVURDERING_TILBAKEKREVING.kode)
        DagensTallUndergruppe.KLAGE -> rad.oppgavetype == "k9klage"
        DagensTallUndergruppe.PUNSJ -> rad.oppgavetype == "k9punsj"
        DagensTallUndergruppe.FEILUTBETALING -> rad.oppgavetype == "k9tilbake"
        DagensTallUndergruppe.UNNTAKSBEHANDLING -> rad.behandlingTypekode == BehandlingType.UNNTAKSBEHANDLING.kode
    }

    private fun hentFraDatabase(): DagensTallResponse {
        val iDag = LocalDate.now()
        val syvDagerSiden = iDag.minusWeeks(1)
        val fjortenDagerSiden = iDag.minusWeeks(2)
        val tjueåtteDagerSiden = iDag.minusWeeks(4)

        val inngangIdag = hentGrupperte(listOf(mottattDato(iDag)), medHelautomatisk = false)
        val inngangSiste7 = hentGrupperte(listOf(mottattDato(syvDagerSiden)), medHelautomatisk = false)
        val inngangSiste14 = hentGrupperte(listOf(mottattDato(fjortenDagerSiden)), medHelautomatisk = false)
        val inngangSiste28 = hentGrupperte(listOf(mottattDato(tjueåtteDagerSiden)), medHelautomatisk = false)
        val ferdigstiltIdag = hentGrupperte(listOf(lukket, ferdigstiltDato(iDag)), medHelautomatisk = true)
        val ferdigstiltSiste7 = hentGrupperte(listOf(lukket, ferdigstiltDato(syvDagerSiden)), medHelautomatisk = true)
        val ferdigstiltSiste14 = hentGrupperte(listOf(lukket, ferdigstiltDato(fjortenDagerSiden)), medHelautomatisk = true)
        val ferdigstiltSiste28 = hentGrupperte(listOf(lukket, ferdigstiltDato(tjueåtteDagerSiden)), medHelautomatisk = true)

        // Månedlige data for siste 6 måneder
        val måneder = (1..12).map { i ->
            val start = YearMonth.now().minusMonths(i.toLong()).atDay(1)
            val slutt = start.plusMonths(1)
            start to slutt
        }
        val månedligInngang = måneder.map { (start, slutt) ->
            hentGrupperte(listOf(mottattDato(start), mottattDatoFør(slutt)), medHelautomatisk = false)
        }
        val månedligFerdigstilt = måneder.map { (start, slutt) ->
            hentGrupperte(listOf(lukket, ferdigstiltDato(start), ferdigstiltDatoFør(slutt)), medHelautomatisk = true)
        }

        val tall = DagensTallHovedgruppe.entries.flatMap { hovedgruppe ->
            val ytelser = hovedgruppeYtelser[hovedgruppe]

            DagensTallUndergruppe.entries.map { undergruppe ->
                fun dagenstallKort(
                    inngang: List<TelleRad>,
                    ferdigstilt: List<TelleRad>,
                    inngangDatoFiltre: List<FeltverdiOppgavefilter>,
                    ferdigstiltDatoFiltre: List<FeltverdiOppgavefilter>
                ): Pair<DagensTallKortDto, DagensTallKortDto> {
                    return Pair(
                        DagensTallKortDto(
                            hovedtall = DagensTallLinjeDto(
                                "Inngang",
                                inngang.tell(ytelser, undergruppe),
                            ),
                            linjer = emptyList()
                        ),
                        DagensTallKortDto(
                            hovedtall = DagensTallLinjeDto(
                                "Ferdigstilt",
                                ferdigstilt.tell(ytelser, undergruppe),
                            ),
                            linjer = listOf(
                                DagensTallLinjeDto(
                                    "manuelt",
                                    ferdigstilt.tell(ytelser, undergruppe, helautomatisk = false),
                                ),
                                DagensTallLinjeDto(
                                    "automatisk",
                                    ferdigstilt.tell(ytelser, undergruppe, helautomatisk = true),
                                )
                            )
                        )
                    )
                }

                val idag = dagenstallKort(inngangIdag, ferdigstiltIdag, listOf(mottattDato(iDag)), listOf(ferdigstiltDato(iDag)))
                val siste7Dager = dagenstallKort(inngangSiste7, ferdigstiltSiste7, listOf(mottattDato(syvDagerSiden)), listOf(ferdigstiltDato(syvDagerSiden)))
                val siste14Dager = dagenstallKort(inngangSiste14, ferdigstiltSiste14, listOf(mottattDato(fjortenDagerSiden)), listOf(ferdigstiltDato(fjortenDagerSiden)))
                val siste28Dager = dagenstallKort(inngangSiste28, ferdigstiltSiste28, listOf(mottattDato(tjueåtteDagerSiden)), listOf(ferdigstiltDato(tjueåtteDagerSiden)))

                val månedSerier = måneder.mapIndexed { index, (start, slutt) ->
                    YearMonth.from(start).toString() to dagenstallKort(
                        månedligInngang[index], månedligFerdigstilt[index],
                        listOf(mottattDato(start), mottattDatoFør(slutt)),
                        listOf(ferdigstiltDato(start), ferdigstiltDatoFør(slutt))
                    )
                }

                DagensTallDto(
                    hovedgruppe = hovedgruppe,
                    undergruppe = undergruppe,
                    serier = mapOf(
                        "idag" to idag,
                        "siste7Dager" to siste7Dager,
                        "siste14Dager" to siste14Dager,
                        "siste28Dager" to siste28Dager,
                    ) + månedSerier
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
