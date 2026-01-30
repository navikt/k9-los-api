package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.produksjonsstyring.UtvidetSøknadÅrsak
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.*
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.KodeverkDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.KodeverkVerdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon as KlageAksjonspunktDefinisjon
import no.nav.k9.kodeverk.api.Kodeverdi as KodeverdiK9Sak

//TODO håndtere kodeverksynlighet.skjult
class OmrådeSetup(
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val config: Configuration,
) {
    private val log: Logger = LoggerFactory.getLogger(OmrådeSetup::class.java)
    private val område: String = "K9"

    fun setup() {
        opprettOmråde()
        oppdaterKodeverk()
        oppdaterFeltdefinisjoner()

        ajourholdOppgavetype("/adapterdefinisjoner/k9-oppgavetyper-k9sak.json", config.k9FrontendUrl())
        ajourholdOppgavetype("/adapterdefinisjoner/k9-oppgavetyper-k9klage.json", config.k9FrontendUrl())
        ajourholdOppgavetype("/adapterdefinisjoner/k9-oppgavetyper-k9tilbake.json", config.k9FrontendUrl())
        ajourholdOppgavetype("/adapterdefinisjoner/k9-oppgavetyper-k9punsj.json", config.k9PunsjFrontendUrl())
    }

    private fun opprettOmråde() {
        log.info("oppretter område $område")
        områdeRepository.lagre(område)
    }

    private fun oppdaterFeltdefinisjoner() {
        val objectMapper = jacksonObjectMapper()
        val feltdefinisjonerDto = objectMapper.readValue(
            OmrådeSetup::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("Oppretter/oppdaterer feltdefinisjoner for område $område")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    @WithSpan
    private fun ajourholdOppgavetype(oppgavedefinisjon: String, frontendUrl: String) {
        val oppgavetyperDto = LosObjectMapper.instance.readValue(
            OmrådeSetup::class.java.getResource(oppgavedefinisjon)!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(
            oppgavetyperDto.copy(
                oppgavetyper = oppgavetyperDto.oppgavetyper.map { oppgavetypeDto ->
                    oppgavetypeDto.copy(
                        oppgavebehandlingsUrlTemplate = oppgavetypeDto.oppgavebehandlingsUrlTemplate.replace(
                            "{baseUrl}",
                            frontendUrl
                        )
                    )
                }.toSet()
            )
        )
        log.info("opprettet oppgavetype: $oppgavedefinisjon")
    }

    private fun oppdaterKodeverk() {
        kodeverkFagsystem()
        kodeverkAksjonspunkt()
        kodeverkResultattype()
        kodeverkYtelsetype()
        kodeverkBehandlingstatus()
        kodeverkBehandlingtype()
        kodeverkVenteårsak()
        kodeverkBehandlingssteg()
        kodeverkSøknadsårsak()
        kodeverkBehandlingsårsak()
        kodeverkBehandlendeEnhet()
    }

    private fun kodeverkBehandlendeEnhet() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "behandlendeEnhet",
            beskrivelse = null,
            uttømmende = false,
            verdier = BehandlendeEnhet.entries.lagDto(beskrivelse = null)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkAksjonspunkt() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Aksjonspunkt",
            beskrivelse = null,
            uttømmende = false,
            verdier = aksjonspunktVerdierK9Sak().plus(aksjonspunktVerdierK9Klage())
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun aksjonspunktVerdierK9Sak(): List<KodeverkVerdiDto> {
        return AksjonspunktDefinisjon.entries
            .filterNot { it == AksjonspunktDefinisjon.UNDEFINED }
            .map { apDefinisjon ->
                val (gruppering, synlighet) = aksjonspunktGrupperingForStegtype(apDefinisjon)
                KodeverkVerdiDto(
                    verdi = apDefinisjon.kode,
                    visningsnavn = apDefinisjon.kode + " - " + apDefinisjon.navn,
                    beskrivelse = null,
                    gruppering = gruppering,
                    favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT
                )
            }
    }

    private fun aksjonspunktGrupperingForStegtype(ap: KlageAksjonspunktDefinisjon): Pair<String, KodeverkSynlighet> {
        return when (ap.behandlingSteg) {
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_FORMKRAV_KLAGE_FØRSTEINSTANS,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS -> "Førsteinstans klage" to KodeverkSynlighet.SYNLIG_FAVORITT

            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_FORMKRAV_KLAGE_ANDREINSTANS,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_KLAGE_ANDREINSTANS,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.OVERFØRT_NK -> "Klageinstans" to KodeverkSynlighet.SYNLIG_FAVORITT

            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.FORESLÅ_VEDTAK,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.FATTE_VEDTAK,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.IVERKSETT_VEDTAK -> "Vedtak" to KodeverkSynlighet.SYNLIG_FAVORITT

            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.KONTROLLER_FAKTA -> "Kompletthet" to KodeverkSynlighet.SYNLIG_FAVORITT
        }
    }

    private fun aksjonspunktGrupperingForStegtype(ap: AksjonspunktDefinisjon): Pair<String, KodeverkSynlighet> {
        return when (ap.behandlingSteg) {
            BehandlingStegType.BEREGN_YTELSE,
            BehandlingStegType.PRECONDITION_BEREGNING,
            BehandlingStegType.FASTSETT_OPPTJENINGSPERIODE,
            BehandlingStegType.FASTSETT_SKJÆRINGSTIDSPUNKT_BEREGNING,
            BehandlingStegType.VURDER_VILKAR_BERGRUNN,
            BehandlingStegType.VURDER_REF_BERGRUNN,
            BehandlingStegType.FORDEL_BEREGNINGSGRUNNLAG,
            BehandlingStegType.FORESLÅ_BEREGNINGSGRUNNLAG,
            BehandlingStegType.FORESLÅ_BEHANDLINGSRESULTAT,
            BehandlingStegType.FORTSETT_FORESLÅ_BEREGNINGSGRUNNLAG,
            BehandlingStegType.VURDER_TILKOMMET_INNTEKT,
            BehandlingStegType.FASTSETT_BEREGNINGSGRUNNLAG -> "Beregning" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.VURDER_MANUELT_BREV,
            BehandlingStegType.FORESLÅ_VEDTAK,
            BehandlingStegType.IVERKSETT_VEDTAK,
            BehandlingStegType.FATTE_VEDTAK -> "Vedtak" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.SIMULER_OPPDRAG,
            BehandlingStegType.VURDER_TILBAKETREKK,
            BehandlingStegType.HINDRE_TILBAKETREKK -> "Tilkjent Ytelse/Simulering" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.INIT_PERIODER,
            BehandlingStegType.INIT_VILKÅR,
            BehandlingStegType.INNHENT_PERSONOPPLYSNINGER,
            BehandlingStegType.INNHENT_REGISTEROPP,
            BehandlingStegType.INNHENT_SØKNADOPP,
            BehandlingStegType.INREG_AVSL, //?
            BehandlingStegType.VARSEL_REVURDERING,
            BehandlingStegType.VULOMED,
            BehandlingStegType.VURDER_MEDLEMSKAPVILKÅR,
            BehandlingStegType.VURDER_OMSORG_FOR,
            BehandlingStegType.ALDERSVILKÅRET,
            BehandlingStegType.VURDER_ALDERSVILKÅR_BARN,
            BehandlingStegType.VURDER_ALDERSVILKÅR_BARN_V2,
            BehandlingStegType.VURDER_OPPTJENING_FAKTA,
            BehandlingStegType.VURDER_OPPTJENINGSVILKÅR,
            BehandlingStegType.VURDER_UTLAND,
            BehandlingStegType.VURDER_SØKNADSFRIST -> "Inngangsvilkår" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.KONTROLLER_FAKTA,
            BehandlingStegType.VURDER_KOMPLETTHET_BEREGNING,
            BehandlingStegType.INNHENT_INNTEKTSMELDING,
            BehandlingStegType.KONTROLLER_FAKTA_BEREGNING,
            BehandlingStegType.KONTROLLER_FAKTA_UTTAK,
            BehandlingStegType.KONTROLLER_LØPENDE_MEDLEMSKAP,
            BehandlingStegType.KONTROLLERER_SØKERS_OPPLYSNINGSPLIKT,
            BehandlingStegType.VURDER_KOMPLETTHET,
            BehandlingStegType.POSTCONDITION_KOMPLETTHET,
            BehandlingStegType.KONTROLLER_FAKTA_ARBEIDSFORHOLD -> "Kompletthet" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.VURDER_UTTAK,
            BehandlingStegType.VURDER_UTTAK_V2,
            BehandlingStegType.BEKREFT_UTTAK,
            BehandlingStegType.VURDER_STARTDATO_UTTAKSREGLER -> "Uttak" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.OVERGANG_FRA_INFOTRYGD,
            BehandlingStegType.VURDER_MEDISINSKE_VILKÅR -> "Pleiepenger" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.VURDER_OPPLÆRING_VILKÅR,
            BehandlingStegType.VURDER_INSTITUSJON_VILKÅR -> "Opplæringspenger" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.MANUELL_VILKÅRSVURDERING,
            BehandlingStegType.MANUELL_TILKJENNING_YTELSE -> "Omsorgspenger" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingStegType.VARIANT_FILTER,
            BehandlingStegType.POST_VURDER_MEDISINSKVILKÅR,
            BehandlingStegType.VURDER_INNSYN -> "Øvrige aksjonspunkter" to KodeverkSynlighet.SYNLIG

            BehandlingStegType.VURDER_FARESIGNALER,
            BehandlingStegType.START_STEG -> "Start" to KodeverkSynlighet.SKJULT
        }
    }

    // TODO: Denne gruppering-funksjonen er midlertidig for å iverta dagens gruppering.
    //  Den bør erstattes med en løsning som bruker enten skjermlenkeType eller behandlingSteg.
    private fun grupperingK9Sak(ap: AksjonspunktDefinisjon): Pair<String, Boolean> {
        if (ap.erAutopunkt()) return "Autopunkt k9-sak" to false

        return when (ap) {
            AksjonspunktDefinisjon.AVKLAR_FORTSATT_MEDLEMSKAP,
            AksjonspunktDefinisjon.KONTROLLER_OPPLYSNINGER_OM_SØKNADSFRIST,
            AksjonspunktDefinisjon.VURDER_OPPTJENINGSVILKÅRET,
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING,
            AksjonspunktDefinisjon.VURDER_OMSORGEN_FOR_V2,
            AksjonspunktDefinisjon.AVKLAR_VERGE,
            AksjonspunktDefinisjon.AUTOMATISK_MARKERING_AV_UTENLANDSSAK -> "Innledende behandling" to true

            AksjonspunktDefinisjon.VURDER_NATTEVÅK,
            AksjonspunktDefinisjon.VURDER_BEREDSKAP,
            AksjonspunktDefinisjon.VURDER_RETT_ETTER_PLEIETRENGENDES_DØD -> "Om barnet" to true

            AksjonspunktDefinisjon.AVKLAR_KOMPLETT_NOK_FOR_BEREGNING,
            AksjonspunktDefinisjon.ENDELIG_AVKLAR_KOMPLETT_NOK_FOR_BEREGNING,
            AksjonspunktDefinisjon.MANGLER_AKTIVITETER -> "Mangler inntektsmelding" to true

            AksjonspunktDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS,
            AksjonspunktDefinisjon.VURDER_VARIG_ENDRET_ELLER_NYOPPSTARTET_NÆRING_SELVSTENDIG_NÆRINGSDRIVENDE,
            AksjonspunktDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_FOR_SN_NY_I_ARBEIDSLIVET,
            AksjonspunktDefinisjon.FORDEL_BEREGNINGSGRUNNLAG,
            AksjonspunktDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_TIDSBEGRENSET_ARBEIDSFORHOLD,
            AksjonspunktDefinisjon.AVKLAR_AKTIVITETER,
            AksjonspunktDefinisjon.VURDER_FAKTA_FOR_ATFL_SN,
            AksjonspunktDefinisjon.VURDER_FEILUTBETALING,
            AksjonspunktDefinisjon.OVERSTYRING_AV_BEREGNINGSAKTIVITETER,
            AksjonspunktDefinisjon.OVERSTYRING_AV_BEREGNINGSGRUNNLAG -> "Beregning" to true

            AksjonspunktDefinisjon.OVERSTYR_BEREGNING_INPUT,
            AksjonspunktDefinisjon.TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE,
            AksjonspunktDefinisjon.TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE_ANNEN_PART -> "Flyttesaker" to true

            AksjonspunktDefinisjon.FORESLÅ_VEDTAK,
            AksjonspunktDefinisjon.FORESLÅ_VEDTAK_MANUELT,
            AksjonspunktDefinisjon.VURDERE_ANNEN_YTELSE_FØR_VEDTAK,
            AksjonspunktDefinisjon.VURDERE_DOKUMENT_FØR_VEDTAK -> "Fatte vedtak" to true

            AksjonspunktDefinisjon.VURDER_DATO_NY_REGEL_UTTAK,
            AksjonspunktDefinisjon.VURDER_OVERLAPPENDE_SØSKENSAKER,
            AksjonspunktDefinisjon.VURDER_NYOPPSTARTET -> "Uttak" to true

            AksjonspunktDefinisjon.KONTROLL_AV_MANUELT_OPPRETTET_REVURDERINGSBEHANDLING,
            AksjonspunktDefinisjon.VURDER_REFUSJON_BERGRUNN -> "Uspesifisert" to true

            else -> "Øvrige aksjonspunkter k9-sak" to false
        }
    }

    private fun grupperingK9Klage(ap: KlageAksjonspunktDefinisjon): Pair<String, Boolean> {
        if (ap.erAutopunkt()) return "Autopunkt k9-klage" to false

        return when (ap) {
            KlageAksjonspunktDefinisjon.FORESLÅ_VEDTAK,
            KlageAksjonspunktDefinisjon.VURDERE_DOKUMENT_FØR_VEDTAK -> "Fatte vedtak" to true

            else -> "Øvrige aksjonspunkter k9-klage" to false
        }
    }

    private fun aksjonspunktVerdierK9Klage() =
        KlageAksjonspunktDefinisjon.entries
            .filterNot { it == KlageAksjonspunktDefinisjon.UNDEFINED }
            .map { apDefinisjon ->
                val (gruppering, synlighet) = aksjonspunktGrupperingForStegtype(apDefinisjon)
                KodeverkVerdiDto(
                    verdi = KlageEventTilOppgaveMapper.KLAGE_PREFIX + apDefinisjon.kode,
                    visningsnavn = apDefinisjon.kode + " - " + KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + apDefinisjon.navn,
                    beskrivelse = null,
                    gruppering = gruppering,
                    favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT
                )
            }

    private fun kodeverkFagsystem() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Fagsystem",
            beskrivelse = null,
            uttømmende = true,
            verdier = Fagsystem.entries.lagDto(beskrivelse = null)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkResultattype() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Resultattype",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingResultatType.entries.lagK9Dto(beskrivelse = null)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkYtelsetype() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Ytelsetype",
            beskrivelse = null,
            uttømmende = true,
            verdier = FagsakYtelseType.entries.lagDto(null, KodeverkSynlighetRegler::ytelseType)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkBehandlingstatus() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Behandlingsstatus",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingStatus.entries.lagDto(beskrivelse = null)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkBehandlingtype() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Behandlingtype",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingType.entries
                .map { behandlingType ->
                    val (gruppering, favoritt) = grupperingBehandlingtype(behandlingType)
                    KodeverkVerdiDto(
                        verdi = behandlingType.kode,
                        visningsnavn = behandlingType.navn,
                        favoritt = favoritt,
                        beskrivelse = null,
                        gruppering = gruppering
                    )
                }
        )

        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun grupperingBehandlingtype(behandlingType: BehandlingType): Pair<String, Boolean> {
        return when (behandlingType) {
            BehandlingType.FORSTEGANGSSOKNAD,
            BehandlingType.REVURDERING -> "Ordinærbehandling" to true

            BehandlingType.KLAGE -> "Klage" to true

            BehandlingType.TILBAKE,
            BehandlingType.REVURDERING_TILBAKEKREVING -> "Tilbakekreving" to true

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
            BehandlingType.JOURNALPOSTNOTAT,
            BehandlingType.UKJENT -> "Punsj" to true

            else -> "Øvrige behandlingstyper" to false
        }
    }

    private fun kodeverkVenteårsak() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Venteårsak",
            beskrivelse = null,
            uttømmende = true,
            verdier = Venteårsak.entries.lagK9Dto(beskrivelse = null) + no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak.entries.lageK9KlageDto(
                beskrivelse = null,
                prefiks = false
            ),
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkSøknadsårsak() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "søknadsårsak",
            beskrivelse = "Søknadsårsak gjelder omsorgspengesøknader hvor søker har søkt selv i stedet for (eller i tillegg til) arbeidsgiver",
            uttømmende = true,
            verdier = UtvidetSøknadÅrsak.entries.lagK9Dto(beskrivelse = null, KodeverkSynlighetRegler::søknadÅrsak)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkBehandlingsårsak() {
        val kodeverk = KodeverkDto(
            område = område,
            eksternId = "behandlingsårsak",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingÅrsakType.entries
                .map { behandlingÅrsakType ->
                    val (gruppering, synlighet) = grupperingBehandlingsårsakK9Sak(behandlingÅrsakType)
                    KodeverkVerdiDto(
                        verdi = behandlingÅrsakType.kode,
                        visningsnavn = behandlingÅrsakType.navn,
                        favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT,
                        beskrivelse = null,
                        gruppering = gruppering
                    )
                }
                    +
                    no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.entries
                        .map { behandlingÅrsakType ->
                            val (gruppering, synlighet) = grupperingBehandlingsårsakK9Klage(behandlingÅrsakType)
                            KodeverkVerdiDto(
                                verdi = behandlingÅrsakType.kode,
                                visningsnavn = behandlingÅrsakType.navn,
                                favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT,
                                beskrivelse = null,
                                gruppering = gruppering
                            )
                        }
        )

        feltdefinisjonTjeneste.oppdater(kodeverk)
    }

    private fun grupperingBehandlingsårsakK9Klage(behandlingÅrsakType: no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType): Pair<String, KodeverkSynlighet> {
        return when (behandlingÅrsakType) {
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_FEIL_I_LOVANDVENDELSE,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_FEIL_REGELVERKSFORSTÅELSE,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_FEIL_ELLER_ENDRET_FAKTA,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_FEIL_PROSESSUELL,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.KØET_BEHANDLING,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_ANNET,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_KLAGE_UTEN_END_INNTEKT,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_KLAGE_MED_END_INNTEKT,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_OPPLYSNINGER_OM_SØKERS_REL,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.ETTER_KLAGE,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_REGISTEROPPLYSNING,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_OPPLYSNINGER_OM_YTELSER,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_TILSTØTENDE_YTELSE_INNVILGET,
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_TILSTØTENDE_YTELSE_OPPHØRT -> "Fra K9-klage" to KodeverkSynlighet.SYNLIG

            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.UDEFINERT -> "Udefinert" to KodeverkSynlighet.SKJULT
        }
    }

    private fun grupperingBehandlingsårsakK9Sak(behandlingÅrsakType: BehandlingÅrsakType): Pair<String, KodeverkSynlighet> {
        return when (behandlingÅrsakType) {
            BehandlingÅrsakType.UDEFINERT -> "Udefinert" to KodeverkSynlighet.SKJULT

            BehandlingÅrsakType.RE_ETABLERT_TILSYN_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_NATTEVÅKBEREDSKAP_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_NATTEVÅKBEREDSKAP_ETABLERT_TILSYN_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_ETABLERT_TILSYN_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_NATTEVÅK_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_ETABLERT_TILSYN_NATTVÅK_ENDRING_FRA_ANNEN_OMSORGSPERSON -> "Annen omsorgsperson" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_MEDLEMSKAP,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_OPPTJENING,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_FORDELING,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_INNTEKT,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_DØD,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_SØKERS_REL,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_SØKNAD_FRIST,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_BEREGNINGSGRUNNLAG -> "Nye opplysninger" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingÅrsakType.RE_FEIL_I_LOVANDVENDELSE,
            BehandlingÅrsakType.RE_FEIL_REGELVERKSFORSTÅELSE,
            BehandlingÅrsakType.RE_FEIL_ELLER_ENDRET_FAKTA,
            BehandlingÅrsakType.RE_FEIL_PROSESSUELL,
            BehandlingÅrsakType.RE_KLAGE_UTEN_END_INNTEKT,
            BehandlingÅrsakType.RE_KLAGE_MED_END_INNTEKT,
            BehandlingÅrsakType.RE_KLAGE_NY_INNH_LIGNET_INNTEKT,
            BehandlingÅrsakType.RE_KLAGE_NATTEVÅKBEREDSKAP,
            BehandlingÅrsakType.ETTER_KLAGE -> "Klage" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingÅrsakType.RE_HENDELSE_DØD_FORELDER,
            BehandlingÅrsakType.RE_HENDELSE_DØD_BARN -> "Dødsfall" to KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingÅrsakType.RE_MANGLER_FØDSEL,
            BehandlingÅrsakType.RE_MANGLER_FØDSEL_I_PERIODE,
            BehandlingÅrsakType.RE_AVVIK_ANTALL_BARN,
            BehandlingÅrsakType.RE_ENDRING_FRA_BRUKER,
            BehandlingÅrsakType.RE_FRAVÆRSKORRIGERING_FRA_SAKSBEHANDLER,
            BehandlingÅrsakType.RE_ENDRET_INNTEKTSMELDING,
            BehandlingÅrsakType.BERØRT_BEHANDLING,
            BehandlingÅrsakType.RE_ANNET,
            BehandlingÅrsakType.RE_SATS_REGULERING,
            BehandlingÅrsakType.RE_ENDRET_FORDELING,
            BehandlingÅrsakType.INFOBREV_BEHANDLING,
            BehandlingÅrsakType.INFOBREV_OPPHOLD,
            BehandlingÅrsakType.RE_HENDELSE_FØDSEL,
            BehandlingÅrsakType.RE_REGISTEROPPLYSNING,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_YTELSER,
            BehandlingÅrsakType.RE_TILSTØTENDE_YTELSE_INNVILGET,
            BehandlingÅrsakType.RE_ENDRING_BEREGNINGSGRUNNLAG,
            BehandlingÅrsakType.RE_TILSTØTENDE_YTELSE_OPPHØRT,
            BehandlingÅrsakType.RE_REBEREGN_FERIEPENGER,
            BehandlingÅrsakType.RE_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_ENDRING_I_EGEN_OVERLAPPENDE_SAK,
            BehandlingÅrsakType.RE_UTSATT_BEHANDLING,
            BehandlingÅrsakType.RE_GJENOPPTAR_UTSATT_BEHANDLING,
            BehandlingÅrsakType.RE_FERIEPENGER_ENDRING_FRA_ANNEN_SAK,
            BehandlingÅrsakType.UNNT_GENERELL,
            BehandlingÅrsakType.REVURDERER_BERØRT_PERIODE -> "Øvrige årsaker" to KodeverkSynlighet.SYNLIG

            else -> "Øvrige årsaker" to KodeverkSynlighet.SYNLIG
        }


    }

    private fun kodeverkBehandlingssteg() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Behandlingssteg",
            beskrivelse = null,
            uttømmende = false,
            verdier = BehandlingStegType.entries.lagK9Dto(beskrivelse = null)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    fun <T : Kodeverdi> Collection<T>.lagDto(
        beskrivelse: String?,
        synlighet: (T) -> KodeverkSynlighet = { KodeverkSynlighet.SYNLIG_FAVORITT }
    ): List<KodeverkVerdiDto> {
        return associateWith { synlighet(it) }
            .filter { (_, synlighet) -> synlighet != KodeverkSynlighet.SKJULT }
            .map { (kodeverdi, synlighet) ->
                KodeverkVerdiDto(
                    verdi = kodeverdi.kode,
                    visningsnavn = kodeverdi.navn,
                    beskrivelse = beskrivelse,
                    favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT
                )
            }.sortedBy { it.visningsnavn }
    }

    fun <T : KodeverdiK9Sak> Collection<T>.lagK9Dto(
        beskrivelse: String?,
        synlighet: (T) -> KodeverkSynlighet = { KodeverkSynlighet.SYNLIG_FAVORITT }
    ): List<KodeverkVerdiDto> {
        return associateWith { synlighet(it) }
            .filter { (_, synlighet) -> synlighet != KodeverkSynlighet.SKJULT }
            .map { (kodeverdi, synlighet) ->
                KodeverkVerdiDto(
                    verdi = kodeverdi.kode,
                    visningsnavn = kodeverdi.navn,
                    beskrivelse = beskrivelse,
                    favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT
                )
            }.sortedBy { it.visningsnavn }
    }

    fun <T : no.nav.k9.klage.kodeverk.api.Kodeverdi> Collection<T>.lageK9KlageDto(
        beskrivelse: String?,
        prefiks: Boolean,
        synlighet: (T) -> KodeverkSynlighet = { KodeverkSynlighet.SYNLIG_FAVORITT }
    ): List<KodeverkVerdiDto> {
        return associateWith { synlighet(it) }
            .filter { (_, synlighet) -> synlighet != KodeverkSynlighet.SKJULT }
            .map { (kodeverdi, synlighet) ->
                KodeverkVerdiDto(
                    verdi = (if (prefiks) KlageEventTilOppgaveMapper.KLAGE_PREFIX else "") + kodeverdi.kode,
                    visningsnavn = KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + kodeverdi.navn,
                    beskrivelse = beskrivelse,
                    favoritt = synlighet == KodeverkSynlighet.SYNLIG_FAVORITT
                )
            }.sortedBy { it.visningsnavn }
    }
}

object KodeverkSynlighetRegler {
    fun behandlingType(behandlingType: BehandlingType): KodeverkSynlighet {
        return when (behandlingType) {
            BehandlingType.ANKE -> KodeverkSynlighet.SKJULT
            BehandlingType.FORSTEGANGSSOKNAD,
            BehandlingType.REVURDERING,
            BehandlingType.REVURDERING_TILBAKEKREVING -> KodeverkSynlighet.SYNLIG_FAVORITT

            else -> KodeverkSynlighet.SYNLIG
        }
    }

    fun søknadÅrsak(søknadÅrsak: UtvidetSøknadÅrsak): KodeverkSynlighet {
        return when (søknadÅrsak) {
            else -> KodeverkSynlighet.SYNLIG_FAVORITT
        }
    }


    fun ytelseType(ytelseType: FagsakYtelseType): KodeverkSynlighet {
        return when (ytelseType) {
            FagsakYtelseType.FRISINN,
            FagsakYtelseType.UNGDOMSYTELSE,
            FagsakYtelseType.OMSORGSDAGER -> KodeverkSynlighet.SKJULT

            FagsakYtelseType.UKJENT -> KodeverkSynlighet.SYNLIG

            else -> KodeverkSynlighet.SYNLIG_FAVORITT
        }
    }
}

enum class KodeverkSynlighet {
    SKJULT,
    SYNLIG,
    SYNLIG_FAVORITT;
}