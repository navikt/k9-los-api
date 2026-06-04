package no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.dagenstall

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.uthenting.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.uthenting.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.uthenting.query.dto.query.*
import no.nav.k9.los.nyoppgavestyring.uthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.nøkkeltall.KodeOgNavn
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
        val omsorgspenger = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.OMSORGSPENGER.kode))
        val opplæringspenger = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.OLP.kode))
        val psb = FeltverdiOppgavefilter("K9", "ytelsestype", EksternFeltverdiOperator.IN, listOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode))

        val mottattDato = { dato: LocalDate -> FeltverdiOppgavefilter("K9", "mottattDato", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(dato.toString())) }
        val ferdigstiltDato = { dato: LocalDate -> FeltverdiOppgavefilter(null, "ferdigstiltDato", EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf(dato.toString())) }
        val lukket = FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.LUKKET.kode))

        val klage = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9klage"))
        val punsj = FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9punsj"))

        private val hovedgruppeYtelser: Map<DagensTallHovedgruppe, Set<String>?> = mapOf(
            DagensTallHovedgruppe.ALLE to null,
            DagensTallHovedgruppe.OMSORGSPENGER to setOf(FagsakYtelseType.OMSORGSPENGER.kode),
            DagensTallHovedgruppe.OMSORGSDAGER to setOf(FagsakYtelseType.OMSORGSDAGER, FagsakYtelseType.OMSORGSPENGER_KS, FagsakYtelseType.OMSORGSPENGER_AO, FagsakYtelseType.OMSORGSPENGER_MA).map { it.kode }.toSet(),
            DagensTallHovedgruppe.OPPLÆRINGSPENGER to setOf(FagsakYtelseType.OLP.kode),
            DagensTallHovedgruppe.PLEIEPENGER_SYKT_BARN to setOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode),
            DagensTallHovedgruppe.PPN to setOf(FagsakYtelseType.PPN.kode),
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

        return resultat.map { rad ->
            TelleRad(
                ytelsestype = rad.feltverdier.find { it.kode == "ytelsestype" }?.verdi?.toString(),
                oppgavetype = rad.feltverdier.find { it.kode == "oppgavetype" }?.verdi?.toString(),
                behandlingTypekode = rad.feltverdier.find { it.kode == "behandlingTypekode" }?.verdi?.toString(),
                helautomatisk = if (medHelautomatisk) rad.feltverdier.find { it.kode == "helautomatiskBehandlet" }?.verdi?.toString() else null,
                antall = checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi) as Long
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
        val enUke = iDag.minusWeeks(1)
        val toUkerSiden = iDag.minusWeeks(2)
        val fireUkerSiden = iDag.minusWeeks(4)

        val inngangIdag = hentGrupperte(listOf(mottattDato(iDag)), medHelautomatisk = false)
        val inngangSisteUke = hentGrupperte(listOf(mottattDato(enUke)), medHelautomatisk = false)
        val inngangSiste2Uker = hentGrupperte(listOf(mottattDato(toUkerSiden)), medHelautomatisk = false)
        val inngangSiste4Uker = hentGrupperte(listOf(mottattDato(fireUkerSiden)), medHelautomatisk = false)
        val ferdigstiltIdag = hentGrupperte(listOf(lukket, ferdigstiltDato(iDag)), medHelautomatisk = true)
        val ferdigstiltSisteUke = hentGrupperte(listOf(lukket, ferdigstiltDato(enUke)), medHelautomatisk = true)
        val ferdigstiltSiste2Uker = hentGrupperte(listOf(lukket, ferdigstiltDato(toUkerSiden)), medHelautomatisk = true)
        val ferdigstiltSiste4Uker = hentGrupperte(listOf(lukket, ferdigstiltDato(fireUkerSiden)), medHelautomatisk = true)

        val tall = DagensTallHovedgruppe.entries.flatMap { hovedgruppe ->
            val ytelser = hovedgruppeYtelser[hovedgruppe]

            DagensTallUndergruppe.entries.map { undergruppe ->
                fun dagenstallKort(
                    inngang: List<TelleRad>,
                    ferdigstilt: List<TelleRad>,
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

                val idag = dagenstallKort(inngangIdag, ferdigstiltIdag)
                val siste7Dager = dagenstallKort(inngangSisteUke, ferdigstiltSisteUke)
                val siste14Dager = dagenstallKort(inngangSiste2Uker, ferdigstiltSiste2Uker)
                val siste28Dager = dagenstallKort(inngangSiste4Uker, ferdigstiltSiste4Uker)

                DagensTallDto(
                    hovedgruppe = hovedgruppe,
                    undergruppe = undergruppe,
                    serier = mapOf(
                        "idag" to idag,
                        "sisteUke" to siste7Dager,
                        "siste2Uker" to siste14Dager,
                        "siste4Uker" to siste28Dager,
                    ),
                    månedSerier = emptyMap()
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
