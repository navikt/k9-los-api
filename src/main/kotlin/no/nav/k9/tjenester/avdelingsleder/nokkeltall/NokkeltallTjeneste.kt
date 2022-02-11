package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.lager.oppgave.AksjonspunktTilstand
import no.nav.k9.domene.modell.AksjonspunktStatus
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.periode.tidligsteOgSeneste
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import java.time.LocalDate

class NokkeltallTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkRepository: StatistikkRepository
) {

    suspend fun hentOppgaverUnderArbeid(): List<AlleOppgaverDto> {
        return oppgaveRepository.hentAlleOppgaverUnderArbeid()
    }

    fun hentOppgaverPåVent(): List<AlleOppgaverHistorikk> {
        val oppgaverPåVent = oppgaveRepository.hentAllePåVent()
        val oppgaverPerBehandlingPåVent = oppgaverPåVent.groupBy {
            BehandlingPåVent(it.fagsakYtelseType, it.behandlingType, it.behandlingsfrist.toLocalDate())
        }
        return oppgaverPerBehandlingPåVent.map { (key, value) ->
            AlleOppgaverHistorikk(key.fagsakYtelseType, key.behandlingType, key.dato, value.size)
        }
    }

    fun hentOppgaverPåVentV2(): OppgaverPåVentDto.PåVentResponse {
        val oppgaverPåVent = oppgaveRepository.hentAllePåVent()

        data class PerBehandling(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate)
        data class PerBehandlingVenteårsak(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate,
                                           val venteårsak: String)

        val påVentPerBehandling = oppgaverPåVent.groupingBy {
            PerBehandling(it.fagsakYtelseType, it.behandlingType, it.behandlingsfrist.toLocalDate())
        }.eachCount().map { (vo, antall) ->  OppgaverPåVentDto.PerBehandlingDto(vo.f, vo.b, vo.frist, antall) }

        val påVentPerVenteårsak = oppgaverPåVent.groupingBy {
            val autopunkt = it.aksjonspunkter.aktivAutopunkt()
            PerBehandlingVenteårsak(
                it.fagsakYtelseType,
                it.behandlingType,
                autopunkt?.frist?.toLocalDate() ?: it.behandlingsfrist.toLocalDate(),
                autopunkt?.venteårsak ?: "UKJENT") // TODO eget kodeverk?
        }.eachCount()
            .map { (vo, antall) -> OppgaverPåVentDto.PerVenteårsakDto(vo.f, vo.b, vo.frist, vo.venteårsak, antall) }
        return OppgaverPåVentDto.PåVentResponse(påVentPerBehandling, påVentPerVenteårsak)
    }

    fun hentNyeFerdigstilteOppgaverOppsummering(): List<AlleOppgaverNyeOgFerdigstilteDto> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(7).map {
            AlleOppgaverNyeOgFerdigstilteDto(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.nye.size,
                    it.ferdigstilteSaksbehandler.size,
                    )
        }
    }

    fun hentFerdigstilteOppgavePrEnhetHistorikk(): Map<LocalDate, Map<String, Int>> {
        val enheterSomSkalUtelatesFraStatistikk = setOf("2103")
        return statistikkRepository.hentFerdigstiltOppgavehistorikk(antallDagerHistorikk = StatistikkRepository.SISTE_8_UKER_I_DAGER)
            .filterNot { enheterSomSkalUtelatesFraStatistikk.contains(it.behandlendeEnhet) }
            .groupBy { it.dato }
            .mapValues { (_, ferdigstiltOppgave) ->
                ferdigstiltOppgave.groupBy { it.behandlendeEnhet }.mapValues { it.value.size }
            }.fyllTommeDagerMedVerdi(emptyMap())
    }

    enum class FerdigstiltHistorikkType { ENHET, YTELSETYPE }
    fun hentFerdigstiltOppgavehistorikk(historikkType: List<FerdigstiltHistorikkType> = FerdigstiltHistorikkType.values().toList()): List<FerdigstillelseHistorikkEnhet> {
        val resultat = mutableMapOf<LocalDate, FerdigstillelseHistorikkEnhet>()

        if (historikkType.contains(FerdigstiltHistorikkType.ENHET)) {
            hentFerdigstilteOppgavePrEnhetHistorikk()
                .mapValues {
                    FerdigstillelseHistorikkEnhet(
                        dato = it.key,
                        behandlendeEnhet = it.value.map { (enhet, antall) ->
                            FerdigstillelseHistorikkEnhet.AntallPrEnhet(enhet, antall)
                        })
                }.run { resultat.putAll(this) }
        }

        if (historikkType.contains(FerdigstiltHistorikkType.YTELSETYPE)) {
            hentFerdigstilteSiste8Uker()
                .groupBy { it.dato }
                .mapValues {
                    it.value.map { verdi ->
                        FerdigstillelseHistorikkEnhet.AntallPrYtelsetype(
                            fagsakYtelseType = verdi.fagsakYtelseType,
                            behandlingType = verdi.behandlingType,
                            antall = verdi.antall
                        )
                    }
                }.forEach { (dato, verdier) ->
                    resultat.getOrPut(dato) { FerdigstillelseHistorikkEnhet(dato) }.ytelseType = verdier
                }
        }

        return resultat.map { it.value }
    }

    fun hentFerdigstilteSiste8Uker(): List<AlleOppgaverHistorikk> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(StatistikkRepository.SISTE_8_UKER_I_DAGER).map {
            AlleOppgaverHistorikk(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.ferdigstilteSaksbehandler.size
            )
        }
    }

    fun hentNyeSiste8Uker(): List<AlleOppgaverHistorikk> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(StatistikkRepository.SISTE_8_UKER_I_DAGER).map {
            AlleOppgaverHistorikk(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.nye.size
            )
        }
    }

    suspend fun hentDagensTall(): List<AlleApneBehandlinger> {
        return oppgaveRepository.hentApneBehandlingerPerBehandlingtypeIdag()
    }
}

private fun Aksjonspunkter.aktivAutopunkt(): AksjonspunktTilstand? {
    if (this.apTilstander == null) return null
    return this.apTilstander.first {
        it.status == AksjonspunktStatus.OPPRETTET
                && it.erAutopunkt()
                && it.venteårsak != null }
}

private fun AksjonspunktTilstand.erAutopunkt() = AksjonspunktDefinisjon.fraKode(this.aksjonspunktKode).erAutopunkt()

fun <T> Map<LocalDate, T>.fyllTommeDagerMedVerdi(verdi: T): Map<LocalDate, T> {
    val resultat = this.toSortedMap()

    tidligsteOgSeneste()?.datoerIPeriode()?.forEach {
        resultat.putIfAbsent(it, verdi)
    }
    return resultat
}



data class FerdigstillelseHistorikkEnhet(
    val dato: LocalDate,
    var behandlendeEnhet: List<AntallPrEnhet>? = null,
    var ytelseType: List<AntallPrYtelsetype>? = null
) {
    data class AntallPrEnhet(
        val enhet: String,
        val antall: Int
    )

    data class AntallPrYtelsetype(
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val antall: Int
    )
}


private data class BehandlingPåVent(
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val dato: LocalDate,
)