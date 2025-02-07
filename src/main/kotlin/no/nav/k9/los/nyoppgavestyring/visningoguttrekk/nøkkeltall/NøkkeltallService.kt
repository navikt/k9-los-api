package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis


class NøkkeltallService(
    private val queryService: OppgaveQueryService,
    private val oppgaverGruppertRepository: OppgaverGruppertRepository,
) {
    private val dagensTallCache = Cache<LocalDate, DagensTallResponse.Suksess>(null)
    private val log: Logger = LoggerFactory.getLogger(NøkkeltallService::class.java)

    fun hentStatus(harTilgangTilKode6: Boolean): List<OppgaverGruppertRepository.BehandlingstypeAntallDto> {
        val grupperte =
            oppgaverGruppertRepository.hentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(harTilgangTilKode6)
        val (medbehandlingType, utenBehandlingType) = grupperte.partition { it.behandlingstype != null }
        if (utenBehandlingType.isNotEmpty()) {
            log.warn(
                "Fant ${
                    utenBehandlingType.map { it.antall }.reduce(Int::plus)
                } oppgaver uten behandlingstype, de blir ikke med oversikt som viser antall"
            )
        }


        val totaltAntall = OppgaverGruppertRepository.BehandlingstypeAntallDto(
            "Åpne behandlinger",
            oppgaverGruppertRepository.hentTotaltAntallÅpneOppgaver(harTilgangTilKode6)
        )

        val punsjtyper = setOf(
            BehandlingType.PAPIRSØKNAD,
            BehandlingType.DIGITAL_SØKNAD,
            BehandlingType.PAPIRETTERSENDELSE,
            BehandlingType.PAPIRINNTEKTSOPPLYSNINGER,
            BehandlingType.DIGITAL_ETTERSENDELSE,
            BehandlingType.INNLOGGET_CHAT,
            BehandlingType.SKRIV_TIL_OSS_SPØRMSÅL,
            BehandlingType.SKRIV_TIL_OSS_SVAR,
            BehandlingType.SAMTALEREFERAT,
            BehandlingType.KOPI,
            BehandlingType.INNTEKTSMELDING_UTGÅTT,
            BehandlingType.UTEN_FNR_DNR,
            BehandlingType.PUNSJOPPGAVE_IKKE_LENGER_NØDVENDIG,
            BehandlingType.UKJENT,
            )
        var punsjSum = 0
        val mutableList = medbehandlingType.toMutableList()
            medbehandlingType.forEach { antallDto ->
            if (punsjtyper.any { it.kode == antallDto.behandlingstype }) {
                punsjSum += antallDto.antall
                mutableList.removeIf { it.behandlingstype == antallDto.behandlingstype }
            }
        }
        mutableList.add(OppgaverGruppertRepository.BehandlingstypeAntallDto("Punsj", punsjSum))

        return listOf(totaltAntall).plus(mutableList)
    }

    fun oppdaterDagensTall(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            dagensTallCache.removeExpiredObjects(LocalDateTime.now())
            val tidBruktPåOppdatering = measureTimeMillis {
                hentDagensTall()
                val dagensTall = hentDagensTall()
                dagensTallCache.set(LocalDate.now(), CacheObject(dagensTall, LocalDateTime.now().plusDays(1)))
            }
            log.info("Oppdaterte dagens tall på $tidBruktPåOppdatering ms")
        }
    }

    fun dagensTall(): DagensTallResponse {
        return dagensTallCache.get(LocalDate.now())?.value
            ?: DagensTallResponse.Feil("Har ikke lastet inn dagens tall ennå")
    }

    private fun hentDagensTall(): DagensTallResponse.Suksess {
        val ytelser = listOf(
            FagsakYtelseType.OMSORGSPENGER,
            FagsakYtelseType.OMSORGSDAGER,
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            FagsakYtelseType.PPN
        )
        val behandlingstyper = listOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.REVURDERING)

        val tall = mutableListOf<DagensTallDto>()

        // Totalt for alle ytelser og behandlingstyper
        tall.add(
            DagensTallDto(
                hovedgruppe = DagensTallHovedgruppe.ALLE,
                undergruppe = DagensTallUndergruppe.TOTALT,
                nyeIDag = hentTall(
                    datotype = Datotype.MOTTATTDATO,
                    operator = EksternFeltverdiOperator.EQUALS,
                    dato = LocalDate.now(),
                ),
                ferdigstilteIDag = hentTall(
                    datotype = Datotype.VEDTAKSDATO,
                    operator = EksternFeltverdiOperator.EQUALS,
                    dato = LocalDate.now(),
                ),
                nyeSiste7Dager = hentTall(
                    datotype = Datotype.MOTTATTDATO,
                    operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    dato = LocalDate.now().minusDays(7),
                ),
                ferdigstilteSiste7Dager = hentTall(
                    datotype = Datotype.VEDTAKSDATO,
                    operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    dato = LocalDate.now().minusDays(7),
                )
            )
        )

        for (ytelseType in ytelser) {
            val hovedgruppe = DagensTallHovedgruppe.fraFagsakYtelseType(ytelseType)

            // Totalt for ytelse
            tall.add(
                DagensTallDto(
                    hovedgruppe = hovedgruppe,
                    undergruppe = DagensTallUndergruppe.TOTALT,
                    nyeIDag = hentTall(
                        datotype = Datotype.MOTTATTDATO,
                        operator = EksternFeltverdiOperator.EQUALS,
                        dato = LocalDate.now(),
                        fagsakYtelseType = ytelseType,
                    ),
                    ferdigstilteIDag = hentTall(
                        datotype = Datotype.VEDTAKSDATO,
                        operator = EksternFeltverdiOperator.EQUALS,
                        dato = LocalDate.now(),
                        fagsakYtelseType = ytelseType,
                    ),
                    nyeSiste7Dager = hentTall(
                        datotype = Datotype.MOTTATTDATO,
                        operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                        dato = LocalDate.now().minusDays(7),
                        fagsakYtelseType = ytelseType,
                    ),
                    ferdigstilteSiste7Dager = hentTall(
                        datotype = Datotype.VEDTAKSDATO,
                        operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                        dato = LocalDate.now().minusDays(7),
                        fagsakYtelseType = ytelseType,
                    )
                )
            )

            for (behandlingType in behandlingstyper) {
                tall.add(
                    DagensTallDto(
                        hovedgruppe = hovedgruppe,
                        undergruppe = DagensTallUndergruppe.fraBehandlingType(behandlingType),
                        nyeIDag = hentTall(
                            datotype = Datotype.MOTTATTDATO,
                            operator = EksternFeltverdiOperator.EQUALS,
                            dato = LocalDate.now(),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        ),
                        ferdigstilteIDag = hentTall(
                            datotype = Datotype.VEDTAKSDATO,
                            operator = EksternFeltverdiOperator.EQUALS,
                            dato = LocalDate.now(),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        ),
                        nyeSiste7Dager = hentTall(
                            datotype = Datotype.MOTTATTDATO,
                            operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                            dato = LocalDate.now().minusDays(7),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        ),
                        ferdigstilteSiste7Dager = hentTall(
                            datotype = Datotype.VEDTAKSDATO,
                            operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                            dato = LocalDate.now().minusDays(7),
                            fagsakYtelseType = ytelseType,
                            behandlingType = behandlingType,
                        )
                    )
                )
            }
        }

        // Punsj
        tall.add(
            DagensTallDto(
                hovedgruppe = DagensTallHovedgruppe.PUNSJ,
                undergruppe = DagensTallUndergruppe.TOTALT,
                nyeIDag = hentTall(
                    datotype = Datotype.MOTTATTDATO,
                    operator = EksternFeltverdiOperator.EQUALS,
                    dato = LocalDate.now(),
                    oppgavetype = "k9punsj",
                ),
                ferdigstilteIDag = hentTall(
                    datotype = Datotype.SISTENDRET,
                    operator = EksternFeltverdiOperator.EQUALS,
                    dato = LocalDate.now(),
                    oppgavetype = "k9punsj",
                    oppgavestatus = Oppgavestatus.LUKKET.kode
                ),
                nyeSiste7Dager = hentTall(
                    datotype = Datotype.MOTTATTDATO,
                    operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    dato = LocalDate.now().minusDays(7),
                    oppgavetype = "k9punsj",
                ),
                ferdigstilteSiste7Dager = hentTall(
                    datotype = Datotype.SISTENDRET,
                    operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    dato = LocalDate.now().minusDays(7),
                    oppgavetype = "k9punsj",
                    oppgavestatus = Oppgavestatus.LUKKET.kode
                )
            )
        )

        return DagensTallResponse.Suksess(
            oppdatertTidspunkt = LocalDateTime.now(),
            hovedgrupper = DagensTallHovedgruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            undergrupper = DagensTallUndergruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            tall = tall
        )
    }

    private fun hentTall(
        datotype: Datotype,
        operator: EksternFeltverdiOperator,
        dato: LocalDate,
        fagsakYtelseType: FagsakYtelseType? = null,
        behandlingType: BehandlingType? = null,
        oppgavetype: String? = null,
        oppgavestatus: String? = null
    ): Long {
        return queryService.queryForAntall(
            QueryRequest(
                oppgaveQuery = OppgaveQuery(
                    filtere = listOfNotNull(
                        fagsakYtelseType?.let {
                            FeltverdiOppgavefilter(
                                "K9", "ytelsestype", EksternFeltverdiOperator.EQUALS.kode, listOf(it.kode)
                            )
                        },
                        behandlingType?.let {
                            FeltverdiOppgavefilter(
                                "K9",
                                "behandlingTypekode",
                                EksternFeltverdiOperator.EQUALS.kode,
                                listOf(it.kode)
                            )
                        },
                        oppgavetype?.let {
                            FeltverdiOppgavefilter(
                                "K9", "oppgavetype", EksternFeltverdiOperator.EQUALS.kode, listOf(it)
                            )
                        },
                        oppgavestatus?.let {
                            FeltverdiOppgavefilter(
                                null, "oppgavestatus", EksternFeltverdiOperator.EQUALS.kode, listOf(it)
                            )
                        },
                        FeltverdiOppgavefilter("K9", datotype.kode, operator.kode, listOf(dato.toString()))
                    )
                ),
                fraAktiv = false,
            )
        )
    }

    private enum class Datotype(val kode: String) {
        VEDTAKSDATO("vedtaksdato"), MOTTATTDATO("mottattDato"), SISTENDRET("sistEndret")
    }
}