package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.AksjonspunktStatus
import no.nav.k9.domene.modell.AksjonspunktTilstand
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.periode.tidligsteOgSeneste
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import java.time.LocalDate
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak as VenteårsakK9Sak

class NokkeltallTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkRepository: StatistikkRepository
) {
    val EnheterSomSkalUtelatesFraStatistikk = setOf("2103")

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
        val påVentPerBehandling = antallOppgaverPåVent(oppgaverPåVent)
        val påVentPerVenteårsak = antallOppgaverPåVentMedÅrsak(oppgaverPåVent)
        return OppgaverPåVentDto.PåVentResponse(påVentPerBehandling, påVentPerVenteårsak)
    }

    private fun antallOppgaverPåVent(oppgaverPåVent: List<Oppgave>): List<OppgaverPåVentDto.PerBehandlingDto> {
        data class PerBehandling(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate)

        return oppgaverPåVent.groupingBy {
            PerBehandling(it.fagsakYtelseType, it.behandlingType, it.behandlingsfrist.toLocalDate())
        }
            .eachCount()
            .map { (vo, antall) -> OppgaverPåVentDto.PerBehandlingDto(vo.f, vo.b, vo.frist, antall) }
    }

    private fun antallOppgaverPåVentMedÅrsak(oppgaverPåVent: List<Oppgave>): List<OppgaverPåVentDto.PerVenteårsakDto> {
        data class PerVenteårsak(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate, val venteårsak: Venteårsak)

        return oppgaverPåVent.groupingBy {
            val autopunkt = it.aksjonspunkter.aktivAutopunkt()
            PerVenteårsak(
                it.fagsakYtelseType,
                it.behandlingType,
                autopunkt?.frist?.toLocalDate() ?: it.behandlingsfrist.toLocalDate(),
                autopunkt?.venteårsak?.tilVenteårsak() ?: Venteårsak.UKJENT
            )
        }
            .eachCount()
            .map { (vo, antall) -> OppgaverPåVentDto.PerVenteårsakDto(vo.f, vo.b, vo.frist, vo.venteårsak, antall) }
    }

    private fun String.tilVenteårsak(): Venteårsak =
        when (this) {
            VenteårsakK9Sak.AVV_DOK.kode
            -> Venteårsak.AVV_DOK
            VenteårsakK9Sak.VENT_MANGL_FUNKSJ_SAKSBEHANDLER.kode
            -> Venteårsak.VENT_MANGL_FUNKSJ_SAKSBEHANDLER
            VenteårsakK9Sak.UTV_FRIST.kode,
            VenteårsakK9Sak.AVV_RESPONS_REVURDERING.kode,
            VenteårsakK9Sak.FOR_TIDLIG_SOKNAD.kode,
            VenteårsakK9Sak.VENT_PÅ_NY_INNTEKTSMELDING_MED_GYLDIG_ARB_ID.kode,
            VenteårsakK9Sak.ANKE_VENTER_PAA_MERKNADER_FRA_BRUKER.kode,
            VenteårsakK9Sak.ANKE_OVERSENDT_TIL_TRYGDERETTEN.kode,
            VenteårsakK9Sak.VENT_OPDT_INNTEKTSMELDING.kode,
            VenteårsakK9Sak.VENT_OPPTJENING_OPPLYSNINGER.kode,
            VenteårsakK9Sak.VENTER_SVAR_PORTEN.kode,
            VenteårsakK9Sak.VENTER_SVAR_TEAMS.kode
            -> Venteårsak.ANNET_MANUELT_SATT_PA_VENT
            else
            -> Venteårsak.AUTOMATISK_SATT_PA_VENT
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

    fun hentFerdigstilteDeloppgaverHistorikk(
        vararg historikkType: VelgbartHistorikkfelt
    ): List<HistorikkElementAntall> {
        return statistikkRepository.hentFerdigstiltOppgavehistorikk(antallDagerHistorikk = StatistikkRepository.SISTE_8_UKER_I_DAGER)
            .filterNot { EnheterSomSkalUtelatesFraStatistikk.contains(it.behandlendeEnhet) }
            .feltSelector(*historikkType)
}


    fun hentFerdigstilteBehandlingerPrEnhetHistorikk(): Map<LocalDate, Map<String, Int>> {
        return statistikkRepository.hentFerdigstiltOppgavehistorikk(antallDagerHistorikk = StatistikkRepository.SISTE_8_UKER_I_DAGER)
            .filterNot { EnheterSomSkalUtelatesFraStatistikk.contains(it.behandlendeEnhet) }
            .groupBy { it.dato }
            .mapValues { (_, ferdigstiltOppgave) ->
                ferdigstiltOppgave.groupBy { it.behandlendeEnhet }.mapValues { it.value.size }
            }.fyllTommeDagerMedVerdi(emptyMap())
    }

    fun hentFerdigstiltOppgavehistorikk(historikkType: List<VelgbartHistorikkfelt> = VelgbartHistorikkfelt.values().toList()): List<FerdigstillelseHistorikkEnhet> {
        val resultat = mutableMapOf<LocalDate, FerdigstillelseHistorikkEnhet>()

        if (historikkType.contains(VelgbartHistorikkfelt.ENHET)) {
            hentFerdigstilteBehandlingerPrEnhetHistorikk()
                .mapValues {
                    FerdigstillelseHistorikkEnhet(
                        dato = it.key,
                        behandlendeEnhet = it.value.map { (enhet, antall) ->
                            FerdigstillelseHistorikkEnhet.AntallPrEnhet(enhet, antall)
                        })
                }.run { resultat.putAll(this) }
        }

        if (historikkType.contains(VelgbartHistorikkfelt.YTELSETYPE)) {
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
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(StatistikkRepository.SISTE_8_UKER_I_DAGER)
            .map {
                AlleOppgaverHistorikk(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.ferdigstilteSaksbehandler.size
                )
            }
    }

    fun hentNyeSiste8Uker(): List<AlleOppgaverHistorikk> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(StatistikkRepository.SISTE_8_UKER_I_DAGER)
            .map {
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
    return this.apTilstander.firstOrNull {
        it.status == AksjonspunktStatus.OPPRETTET
                && AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode).erAutopunkt()
                && it.venteårsak != null
    }
}

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