package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.periode.tidligsteOgSeneste
import no.nav.k9.los.domene.repository.NøkkeltallRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak as VenteårsakK9Sak

class NokkeltallTjeneste(
    private val pepClient: IPepClient,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaverGruppertRepository: OppgaverGruppertRepository,
    private val statistikkRepository: StatistikkRepository,
    private val nøkkeltallRepository: NøkkeltallRepository,
    private val nøkkeltallRepositoryV3: NøkkeltallRepositoryV3,
    private val koinProfile: KoinProfile
) {
    private val log = LoggerFactory.getLogger(BehandlingsmigreringTjeneste::class.java)

    fun hentOppgaverPåVent(): OppgaverPåVentDto.PåVentResponse {
        return if (koinProfile == KoinProfile.PROD){
            hentOppgaverPåVentV2()
        } else {
            hentOppgaverPåVentV3()
        }
    }

    fun hentOppgaverPåVentV2(): OppgaverPåVentDto.PåVentResponse {
        val raw = nøkkeltallRepository.hentAllePåVentGruppert()
            //gir ikke helt mening å ha med VENT_PÅ_TILBAKEKREVINGSGRUNNLAG her. Den er vangligvis samtidig med VENT_PÅ_BRUKERTILBAKEMELDING, så ville gitt duplikater her. Frist er også misvisende for aksjonspunktet, k9tilbake vil uansett vente helt til grunnlag kommer
            .filterNot { gruppe -> gruppe.system == Fagsystem.K9TILBAKE && gruppe.aksjonspunktKode ==  "VENT_PÅ_TILBAKEKREVINGSGRUNNLAG"}

        data class PerBehandling(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate)
        val påVentPerBehandling = raw.groupingBy { PerBehandling( it.fagsakYtelseType, it.behandlingType, it.frist) }
            .aggregate { key, accumulator : Int?, element, first -> if (first) element.antall else accumulator!! + element.antall }
            .entries.map { OppgaverPåVentDto.PerBehandlingDto(it.key.f, it.key.b, it.key.frist, it.value) }

        data class PerVenteårsak(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate, val venteårsak: Venteårsak)
        val påVentPerVenteårsak = raw.groupingBy { PerVenteårsak(it.fagsakYtelseType, it.behandlingType, it.frist, tilVenteårsak(it.system, it.venteårsak)) }
            .aggregate { key, accumulator : Int?, element, first -> if (first) element.antall else accumulator!! + element.antall }
            .entries.map { OppgaverPåVentDto.PerVenteårsakDto(it.key.f, it.key.b, it.key.frist, it.key.venteårsak, it.value) }

        return OppgaverPåVentDto.PåVentResponse(påVentPerBehandling, påVentPerVenteårsak)
    }

    fun hentOppgaverPåVentV3(): OppgaverPåVentDto.PåVentResponse {
        val raw = nøkkeltallRepositoryV3.hentAllePåVentGruppert()
            //gir ikke helt mening å ha med VENT_PÅ_TILBAKEKREVINGSGRUNNLAG her. Den er vangligvis samtidig med VENT_PÅ_BRUKERTILBAKEMELDING, så ville gitt duplikater her. Frist er også misvisende for aksjonspunktet, k9tilbake vil uansett vente helt til grunnlag kommer
            .filterNot { gruppe -> gruppe.system == Fagsystem.K9TILBAKE && gruppe.aksjonspunktKode ==  "VENT_PÅ_TILBAKEKREVINGSGRUNNLAG"}

        data class PerBehandling(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate?)
        val påVentPerBehandling = raw.groupingBy { PerBehandling( it.fagsakYtelseType, it.behandlingType, it.frist) }
            .aggregate { key, accumulator : Int?, element, first -> if (first) element.antall else accumulator!! + element.antall }
            .entries.map { OppgaverPåVentDto.PerBehandlingDto(it.key.f, it.key.b, it.key.frist, it.value) }

        data class PerVenteårsak(val f: FagsakYtelseType, val b: BehandlingType, val frist: LocalDate?, val venteårsak: Venteårsak)
        val påVentPerVenteårsak = raw.groupingBy { PerVenteårsak(it.fagsakYtelseType, it.behandlingType, it.frist, tilVenteårsak(it.system, it.venteårsak)) }
            .aggregate { key, accumulator : Int?, element, first -> if (first) element.antall else accumulator!! + element.antall }
            .entries.map { OppgaverPåVentDto.PerVenteårsakDto(it.key.f, it.key.b, it.key.frist, it.key.venteårsak, it.value) }

        return OppgaverPåVentDto.PåVentResponse(påVentPerBehandling, påVentPerVenteårsak)
    }

    private fun tilVenteårsak(system: Fagsystem, venteårsakkode:String) : Venteårsak =
        when (system) {
            Fagsystem.K9SAK -> venteårsakkode.tilVenteårsakK9sak()
            Fagsystem.K9TILBAKE -> venteårsakkode.tilVenteårsakK9Tilbake()
            else -> {
                log.warn("Mangler mapping for system {} for venteårsakskode {} går videre med UKJENT", system, venteårsakkode)
                Venteårsak.UKJENT
            }
        }

    private fun String.tilVenteårsakK9sak(): Venteårsak =
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



    private fun String.tilVenteårsakK9Tilbake(): Venteårsak =
        when (this) {
            "VENT_PÅ_BRUKERTILBAKEMELDING" -> Venteårsak.AVV_DOK //TODO venter på svar fra bruker, lage egen venteårsak?
            "VENT_PÅ_TILBAKEKREVINGSGRUNNLAG" -> Venteårsak.VENTER_SVAR_INTERNT //TODO venter på (maskinell) prosess som skal gå, lage egen venteårsak?
            "AVV_DOK" -> Venteårsak.AVV_DOK
            "UTV_TIL_FRIST" -> Venteårsak.AVV_DOK //TODO venter på svar fra bruker, lage egen venteårsak?
            "ENDRE_TILKJENT_YTELSE" -> Venteårsak.VENTER_SVAR_INTERNT
            "VENT_PÅ_MULIG_MOTREGNING" -> Venteårsak.VENTER_SVAR_INTERNT //TODO venter på prosess som skal gå, lage egen venteårsak?
            "-" -> Venteårsak.UKJENT
            else -> {
                log.warn("Fikk ikke-støttet venteårsak for K9tilbake {}", this)
                Venteårsak.UKJENT
            }
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

    suspend fun hentDagensTall(): List<AlleApneBehandlinger> {
        if (koinProfile == KoinProfile.PROD) {
            return oppgaveRepository.hentApneBehandlingerPerBehandlingtypeIdag()
        } else {
            val harTilgangTilKode6 = pepClient.harTilgangTilKode6()
            val grupperte =  oppgaverGruppertRepository.hentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(harTilgangTilKode6)
            val (medbehandlingType, utenBehandlingType) = grupperte.partition { it.behandlingstype != null }
            if (utenBehandlingType.isNotEmpty()) {
                log.warn("Fant ${utenBehandlingType.map {it.antall}.reduce(Int::plus)} oppgaver uten behandlingstype, de blir ikke med oversikt som viser antall")
            }
            return medbehandlingType.map { AlleApneBehandlinger(BehandlingType.fraKode(it.behandlingstype!!), it.antall) }

        }
    }
}

private fun <T> Map<LocalDate, T>.fyllTommeDagerMedVerdi(verdi: T): Map<LocalDate, T> {
    val resultat = this.toSortedMap()

    tidligsteOgSeneste()?.datoerIPeriode()?.forEach {
        resultat.putIfAbsent(it, verdi)
    }
    return resultat
}


