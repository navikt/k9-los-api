package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.periode.tidligsteOgSeneste
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak as VenteårsakK9Sak

class NokkeltallTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkRepository: StatistikkRepository
) {

    suspend fun hentOppgaverUnderArbeid(): List<AlleOppgaverDto> {
        return oppgaveRepository.hentAlleOppgaverUnderArbeid()
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
            val autopunkt = finnAutopunkt(it)
            PerBehandling(it.fagsakYtelseType, it.behandlingType, autopunkt?.frist?.toLocalDate() ?: it.behandlingsfrist.toLocalDate())
        }
            .eachCount()
            .map { (vo, antall) -> OppgaverPåVentDto.PerBehandlingDto(vo.f, vo.b, vo.frist, antall) }
    }

    private fun antallOppgaverPåVentMedÅrsak(oppgaverPåVent: List<Oppgave>): List<OppgaverPåVentDto.PerVenteårsakDto> {
        data class PerVenteårsak(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate, val venteårsak: Venteårsak)

        return oppgaverPåVent.groupingBy {
            val autopunkt = finnAutopunkt(it) //Tilbakekreving er ikke håndtert enda
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

    private fun finnAutopunkt(it: Oppgave) =
        if (it.system == Fagsystem.K9SAK.kode) it.aksjonspunkter.aktivAutopunkt() else null //Tilbakekreving er ikke håndtert enda

    private fun String.tilVenteårsak(): Venteårsak =
        when (this) {
            VenteårsakK9Sak.AVV_DOK.kode,
            VenteårsakK9Sak.UTV_FRIST.kode,
            VenteårsakK9Sak.AVV_RESPONS_REVURDERING.kode,
            VenteårsakK9Sak.ANKE_OVERSENDT_TIL_TRYGDERETTEN.kode,
            VenteårsakK9Sak.ANKE_VENTER_PAA_MERKNADER_FRA_BRUKER.kode,
            VenteårsakK9Sak.VENT_PÅ_NY_INNTEKTSMELDING_MED_GYLDIG_ARB_ID.kode,
            VenteårsakK9Sak.VENT_OPDT_INNTEKTSMELDING.kode,
            VenteårsakK9Sak.VENT_OPPTJENING_OPPLYSNINGER.kode,
            VenteårsakK9Sak.FOR_TIDLIG_SOKNAD.kode,
            -> Venteårsak.AVV_DOK
            VenteårsakK9Sak.VENT_MANGL_FUNKSJ_SAKSBEHANDLER.kode
            -> Venteårsak.VENT_MANGL_FUNKSJ_SAKSBEHANDLER
            VenteårsakK9Sak.VENTER_SVAR_PORTEN.kode,
            VenteårsakK9Sak.VENTER_SVAR_TEAMS.kode
            -> Venteårsak.VENTER_SVAR_INTERNT
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

    suspend fun hentHastesaker(): List<HasteOppgaveDto> {
        return oppgaveRepository.hentHasteoppgaver().map { HasteOppgaveDto(
            saksnummer = it.fagsakSaksnummer,
            ytelseType = it.fagsakYtelseType,
            opprettet = it.behandlingOpprettet
        )}
    }

    data class HasteOppgaveDto(
        val saksnummer: String,
        val ytelseType: FagsakYtelseType,
        val opprettet: LocalDateTime
    )


    fun hentFerdigstilteOppgaverHistorikk(
        vararg historikkType: VelgbartHistorikkfelt,
        antallDagerHistorikk: Int = StatistikkRepository.SISTE_8_UKER_I_DAGER
    ): List<HistorikkElementAntall> {
        return statistikkRepository.hentFerdigstiltOppgavehistorikk(antallDagerHistorikk = antallDagerHistorikk)
            .filter { EnheterSomSkalUtelatesFraLos.sjekkKanBrukes(it.behandlendeEnhet) }
            .feltSelector(*historikkType)
    }


    fun hentFerdigstilteBehandlingerPrEnhetHistorikk(): Map<LocalDate, Map<String, Int>> {
        return statistikkRepository.hentFerdigstiltOppgavehistorikk(antallDagerHistorikk = StatistikkRepository.SISTE_8_UKER_I_DAGER)
            .filter { EnheterSomSkalUtelatesFraLos.sjekkKanBrukes(it.behandlendeEnhet) }
            .groupBy { it.dato }
            .mapValues { (_, ferdigstiltOppgave) ->
                ferdigstiltOppgave.groupBy { it.behandlendeEnhet!! }.mapValues { it.value.size }
            }.fyllTommeDagerMedVerdi(emptyMap())
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

fun <T> Map<LocalDate, T>.fyllTommeDagerMedVerdi(verdi: T): Map<LocalDate, T> {
    val resultat = this.toSortedMap()

    tidligsteOgSeneste()?.datoerIPeriode()?.forEach {
        resultat.putIfAbsent(it, verdi)
    }
    return resultat
}


