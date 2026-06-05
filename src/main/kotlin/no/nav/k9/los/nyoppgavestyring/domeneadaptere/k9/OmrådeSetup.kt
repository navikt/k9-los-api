package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Ventekategori
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.produksjonsstyring.UtvidetSøknadÅrsak
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.*
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.*
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon as KlageAksjonspunktDefinisjon
import no.nav.k9.kodeverk.api.Kodeverdi as KodeverdiK9Sak

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
                val (gruppering, synlighet, rekkefølge) = aksjonspunktGrupperingForStegtype(apDefinisjon)
                KodeverkVerdiDto(
                    verdi = apDefinisjon.kode,
                    visningsnavn = apDefinisjon.kode + " - " + apDefinisjon.navn,
                    synlighet = synlighet,
                    gruppering = gruppering,
                    rekkefølge = rekkefølge,
                )
            }
    }

    private fun aksjonspunktGrupperingForStegtype(ap: AksjonspunktDefinisjon): Triple<String, Synlighet, Int> {
        return when (ap.behandlingSteg) {
            BehandlingStegType.KONTROLLER_FAKTA,
            BehandlingStegType.VURDER_KOMPLETTHET_BEREGNING,
            BehandlingStegType.INNHENT_INNTEKTSMELDING,
            BehandlingStegType.KONTROLLER_FAKTA_BEREGNING,
            BehandlingStegType.KONTROLLER_FAKTA_UTTAK,
            BehandlingStegType.KONTROLLER_LØPENDE_MEDLEMSKAP,
            BehandlingStegType.KONTROLLERER_SØKERS_OPPLYSNINGSPLIKT,
            BehandlingStegType.VURDER_KOMPLETTHET,
            BehandlingStegType.VURDER_KOMPLETTHET_ETTERSENDELSER,
            BehandlingStegType.POSTCONDITION_KOMPLETTHET,
            BehandlingStegType.KONTROLLER_FAKTA_ARBEIDSFORHOLD -> Triple(
                "Kompletthet",
                Synlighet.OVER_STREKEN,
                1
            )

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
            BehandlingStegType.VURDER_UTLAND_V2,
            BehandlingStegType.VURDER_SØKNADSFRIST,
            BehandlingStegType.VURDER_RETT_FRA_DAG_EN -> Triple("Inngangsvilkår", Synlighet.OVER_STREKEN, 2)

            BehandlingStegType.VURDER_UTTAK,
            BehandlingStegType.VURDER_UTTAK_V2,
            BehandlingStegType.BEKREFT_UTTAK,
            BehandlingStegType.VURDER_STARTDATO_UTTAKSREGLER -> Triple("Uttak", Synlighet.OVER_STREKEN, 3)

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
            BehandlingStegType.FASTSETT_BEREGNINGSGRUNNLAG -> Triple("Beregning", Synlighet.OVER_STREKEN, 4)

            BehandlingStegType.SIMULER_OPPDRAG,
            BehandlingStegType.VURDER_TILBAKETREKK,
            BehandlingStegType.HINDRE_TILBAKETREKK -> Triple(
                "Tilkjent Ytelse/Simulering",
                Synlighet.OVER_STREKEN,
                5
            )

            BehandlingStegType.VURDER_MANUELT_BREV,
            BehandlingStegType.FORESLÅ_VEDTAK,
            BehandlingStegType.IVERKSETT_VEDTAK,
            BehandlingStegType.FATTE_VEDTAK -> Triple("Vedtak", Synlighet.OVER_STREKEN, 6)

            BehandlingStegType.OVERGANG_FRA_INFOTRYGD,
            BehandlingStegType.VURDER_MEDISINSKE_VILKÅR -> Triple("Pleiepenger", Synlighet.OVER_STREKEN, 7)

            BehandlingStegType.MANUELL_VILKÅRSVURDERING,
            BehandlingStegType.MANUELL_TILKJENNING_YTELSE -> Triple("Omsorgspenger", Synlighet.OVER_STREKEN, 8)

            BehandlingStegType.VURDER_OPPLÆRING_VILKÅR,
            BehandlingStegType.VURDER_INSTITUSJON_VILKÅR -> Triple(
                "Opplæringspenger",
                Synlighet.OVER_STREKEN,
                9
            )

            BehandlingStegType.VARIANT_FILTER,
            BehandlingStegType.POST_VURDER_MEDISINSKVILKÅR,
            BehandlingStegType.VURDER_INNSYN -> Triple("Øvrige aksjonspunkter", Synlighet.UNDER_STREKEN, 10)

            BehandlingStegType.VURDER_FARESIGNALER,
            BehandlingStegType.START_STEG -> Triple("Start", Synlighet.SKJULT, 99)
        }
    }

    private fun aksjonspunktVerdierK9Klage() =
        KlageAksjonspunktDefinisjon.entries
            .filterNot { it == KlageAksjonspunktDefinisjon.UNDEFINED }
            .map { apDefinisjon ->
                val (gruppering, synlighet, rekkefølge) = aksjonspunktGrupperingForStegtype(apDefinisjon)
                KodeverkVerdiDto(
                    verdi = KlageEventTilOppgaveMapper.KLAGE_PREFIX + apDefinisjon.kode,
                    visningsnavn = apDefinisjon.kode + " - " + KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + apDefinisjon.navn,
                    synlighet = synlighet,
                    gruppering = gruppering,
                    rekkefølge = rekkefølge
                )
            }

    private fun aksjonspunktGrupperingForStegtype(ap: KlageAksjonspunktDefinisjon): Triple<String, Synlighet, Int> {
        return when (ap.behandlingSteg) {
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_FORMKRAV_KLAGE_FØRSTEINSTANS,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS -> Triple(
                "Førsteinstans klage",
                Synlighet.OVER_STREKEN,
                10
            )

            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_FORMKRAV_KLAGE_ANDREINSTANS,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.VURDER_KLAGE_ANDREINSTANS,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.OVERFØRT_NK -> Triple(
                "Klageinstans",
                Synlighet.OVER_STREKEN,
                11
            )

            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.FORESLÅ_VEDTAK,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.FATTE_VEDTAK,
            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.IVERKSETT_VEDTAK -> Triple(
                "Vedtak",
                Synlighet.OVER_STREKEN,
                6
            )

            no.nav.k9.klage.kodeverk.behandling.BehandlingStegType.KONTROLLER_FAKTA -> Triple(
                "Kompletthet",
                Synlighet.OVER_STREKEN,
                1
            )
        }
    }

    // TODO: Denne gruppering-funksjonen er midlertidig for å iverta dagens gruppering.
    //  Den bør erstattes med en løsning som bruker enten skjermlenkeType eller behandlingSteg.
    // midlertidig kommentert ut, siden vi prototyper annen løsning
    private fun grupperingK9SakAPGammel(ap: AksjonspunktDefinisjon): Pair<String, Boolean> {
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

    // midlertidig kommentert ut, siden vi prototyper annen løsning
    private fun grupperingK9KlageAPGammel(ap: KlageAksjonspunktDefinisjon): Pair<String, Boolean> {
        if (ap.erAutopunkt()) return "Autopunkt k9-klage" to false

        return when (ap) {
            KlageAksjonspunktDefinisjon.FORESLÅ_VEDTAK,
            KlageAksjonspunktDefinisjon.VURDERE_DOKUMENT_FØR_VEDTAK -> "Fatte vedtak" to true

            else -> "Øvrige aksjonspunkter k9-klage" to false
        }
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
        val verdier = FagsakYtelseType.entries.lagDto(null) { KodeverkSynlighetRegler.ytelseType(it) }
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Ytelsetype",
            beskrivelse = null,
            uttømmende = true,
            verdier = verdier
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
                    val (gruppering, synlighet, rekkefølge) = grupperingBehandlingtype(behandlingType)
                    KodeverkVerdiDto(
                        verdi = behandlingType.kode,
                        visningsnavn = behandlingType.navn,
                        synlighet = synlighet,
                        gruppering = gruppering,
                        rekkefølge = rekkefølge
                    )
                }
        )

        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun grupperingBehandlingtype(behandlingType: BehandlingType): Triple<String, Synlighet, Int> {
        return when (behandlingType) {
            BehandlingType.FORSTEGANGSSOKNAD,
            BehandlingType.REVURDERING -> Triple("Ordinærbehandling", Synlighet.OVER_STREKEN, 1)

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
            BehandlingType.UKJENT -> Triple("Punsj", Synlighet.OVER_STREKEN, 2)

            BehandlingType.KLAGE -> Triple("Klage", Synlighet.OVER_STREKEN, 3)

            BehandlingType.TILBAKE,
            BehandlingType.REVURDERING_TILBAKEKREVING -> Triple("Tilbakekreving", Synlighet.OVER_STREKEN, 4)

            else -> Triple("Øvrige behandlingstyper", Synlighet.UNDER_STREKEN, 5)
        }
    }

    private fun kodeverkVenteårsak() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Venteårsak",
            beskrivelse = null,
            uttømmende = true,
            verdier = Venteårsak
                .entries
                .filterNot { venteårsak -> venteårsak.kode.startsWith("FRISINN") }
                .map { venteårsak ->
                    val (gruppering, synlighet, rekkefølge) = grupperingVenteårsak(venteårsak)
                    KodeverkVerdiDto(
                        verdi = venteårsak.kode,
                        visningsnavn = venteårsak.navn,
                        synlighet = synlighet,
                        gruppering = gruppering,
                        rekkefølge = rekkefølge
                    )
                }.plus(
                    no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak
                        .entries
                        .map { venteårsak ->
                            val (gruppering, synlighet, rekkefølge) = grupperingVenteårsakKlage(venteårsak)
                            KodeverkVerdiDto(
                                verdi = KlageEventTilOppgaveMapper.KLAGE_PREFIX + venteårsak.kode,
                                visningsnavn = KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + venteårsak.navn,
                                synlighet = synlighet,
                                gruppering = gruppering,
                                rekkefølge = rekkefølge
                            )
                        }
                )
        )

        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun grupperingVenteårsak(venteårsak: Venteårsak): Triple<String, Synlighet, Int> {
        return when (venteårsak.ventekategori) {
            Ventekategori.AVVENTER_SØKER -> Triple("Avventer søker", Synlighet.OVER_STREKEN, 1)
            Ventekategori.AVVENTER_ARBEIDSGIVER -> Triple("Avventer arbeidsgiver", Synlighet.OVER_STREKEN, 2)
            Ventekategori.AVVENTER_TEKNISK_FEIL -> Triple("Avventer teknisk feil", Synlighet.OVER_STREKEN, 3)
            Ventekategori.AVVENTER_ANNET -> Triple("Avventer annet", Synlighet.OVER_STREKEN, 4)
            Ventekategori.AVVENTER_ANNET_IKKE_SAKSBEHANDLINGSTID -> Triple(
                "Avventer annet (ikke saksbehandlingstid)",
                Synlighet.OVER_STREKEN,
                5
            )

            Ventekategori.AVVENTER_SAKSBEHANDLER -> Triple("Avventer saksbehandler", Synlighet.UNDER_STREKEN, 7)

            else -> Triple("Uspesifisert", Synlighet.UNDER_STREKEN, 8)
        }
    }

    private fun grupperingVenteårsakKlage(venteårsak: no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak): Triple<String, Synlighet, Int> {
        return Triple(
            "Klage",
            Synlighet.OVER_STREKEN,
            6
        ) //TODO: Placeholder før eventuelt mer interessant logikk
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
            verdier =
                BehandlingÅrsakType
                    .entries
                    .map { behandlingÅrsakType ->
                        val (gruppering, synlighet, rekkefølge) = grupperingBehandlingsårsakK9Sak(behandlingÅrsakType)
                        KodeverkVerdiDto(
                            verdi = behandlingÅrsakType.kode,
                            visningsnavn = behandlingÅrsakType.navn,
                            synlighet = synlighet,
                            gruppering = gruppering,
                            rekkefølge = rekkefølge
                        )
                    }
                    .plus(
                        no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType
                            .entries
                            .map { behandlingÅrsakType ->
                                val (gruppering, synlighet, rekkefølge) = grupperingBehandlingsårsakK9Klage(
                                    behandlingÅrsakType
                                )
                                KodeverkVerdiDto(
                                    verdi = behandlingÅrsakType.kode,
                                    visningsnavn = behandlingÅrsakType.navn,
                                    synlighet = synlighet,
                                    gruppering = gruppering,
                                    rekkefølge = rekkefølge,
                                )
                            }
                    )
        )

        feltdefinisjonTjeneste.oppdater(kodeverk)
    }

    private fun grupperingBehandlingsårsakK9Klage(behandlingÅrsakType: no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType): Triple<String, Synlighet, Int> {
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
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.RE_TILSTØTENDE_YTELSE_OPPHØRT -> Triple(
                "Fra K9-klage",
                Synlighet.UNDER_STREKEN,
                5
            )

            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.UDEFINERT -> Triple(
                "Udefinert",
                Synlighet.SKJULT,
                6
            )
        }
    }

    private fun grupperingBehandlingsårsakK9Sak(behandlingÅrsakType: BehandlingÅrsakType): Triple<String, Synlighet, Int> {
        return when (behandlingÅrsakType) {
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_MEDLEMSKAP,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_OPPTJENING,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_FORDELING,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_INNTEKT,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_DØD,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_SØKERS_REL,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_SØKNAD_FRIST,
            BehandlingÅrsakType.RE_OPPLYSNINGER_OM_BEREGNINGSGRUNNLAG -> Triple(
                "Nye opplysninger",
                Synlighet.OVER_STREKEN,
                1
            )

            BehandlingÅrsakType.RE_ETABLERT_TILSYN_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_NATTEVÅKBEREDSKAP_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_NATTEVÅKBEREDSKAP_ETABLERT_TILSYN_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_ETABLERT_TILSYN_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_NATTEVÅK_ENDRING_FRA_ANNEN_OMSORGSPERSON,
            BehandlingÅrsakType.RE_SYKDOM_ETABLERT_TILSYN_NATTVÅK_ENDRING_FRA_ANNEN_OMSORGSPERSON -> Triple(
                "Annen omsorgsperson",
                Synlighet.OVER_STREKEN,
                2
            )

            BehandlingÅrsakType.RE_HENDELSE_DØD_FORELDER,
            BehandlingÅrsakType.RE_HENDELSE_DØD_BARN -> Triple("Dødsfall", Synlighet.OVER_STREKEN, 3)

            BehandlingÅrsakType.RE_FEIL_I_LOVANDVENDELSE,
            BehandlingÅrsakType.RE_FEIL_REGELVERKSFORSTÅELSE,
            BehandlingÅrsakType.RE_FEIL_ELLER_ENDRET_FAKTA,
            BehandlingÅrsakType.RE_FEIL_PROSESSUELL,
            BehandlingÅrsakType.RE_KLAGE_UTEN_END_INNTEKT,
            BehandlingÅrsakType.RE_KLAGE_MED_END_INNTEKT,
            BehandlingÅrsakType.RE_KLAGE_NY_INNH_LIGNET_INNTEKT,
            BehandlingÅrsakType.RE_KLAGE_NATTEVÅKBEREDSKAP,
            BehandlingÅrsakType.ETTER_KLAGE -> Triple("Klage", Synlighet.OVER_STREKEN, 4)

            BehandlingÅrsakType.UDEFINERT -> Triple("Udefinert", Synlighet.SKJULT, 99)

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
            BehandlingÅrsakType.REVURDERER_BERØRT_PERIODE -> Triple(
                "Øvrige årsaker",
                Synlighet.UNDER_STREKEN,
                6
            )

            else -> Triple("Øvrige årsaker", Synlighet.UNDER_STREKEN, 6)
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
        finnSynlighetOgRekkefølge: (T) -> Pair<Synlighet, Int?> = { Synlighet.OVER_STREKEN to null }
    ): List<KodeverkVerdiDto> {
        return associateWith { finnSynlighetOgRekkefølge(it) }
            .filter { (_, synlighetOgRekkefølge) -> synlighetOgRekkefølge.first != Synlighet.SKJULT }
            .map { (kodeverdi, synlighetOgRekkefølge) ->
                KodeverkVerdiDto(
                    verdi = kodeverdi.kode,
                    visningsnavn = kodeverdi.navn,
                    synlighet = synlighetOgRekkefølge.first,
                    rekkefølge = synlighetOgRekkefølge.second,
                    beskrivelse = beskrivelse
                )
            }.sortedBy { it.visningsnavn }
    }

    fun <T : KodeverdiK9Sak> Collection<T>.lagK9Dto(
        beskrivelse: String?,
        synlighet: (T) -> Synlighet = { Synlighet.OVER_STREKEN }
    ): List<KodeverkVerdiDto> {
        return associateWith { synlighet(it) }
            .filter { (_, synlighet) -> synlighet != Synlighet.SKJULT }
            .map { (kodeverdi, synlighet) ->
                KodeverkVerdiDto(
                    verdi = kodeverdi.kode,
                    visningsnavn = kodeverdi.navn,
                    synlighet = synlighet,
                    beskrivelse = beskrivelse
                )
            }.sortedBy { it.visningsnavn }
    }

    fun <T : no.nav.k9.klage.kodeverk.api.Kodeverdi> Collection<T>.lageK9KlageDto(
        beskrivelse: String?,
        prefiks: Boolean,
        synlighet: (T) -> Synlighet = { Synlighet.OVER_STREKEN }
    ): List<KodeverkVerdiDto> {
        return associateWith { synlighet(it) }
            .filter { (_, synlighet) -> synlighet != Synlighet.SKJULT }
            .map { (kodeverdi, synlighet) ->
                KodeverkVerdiDto(
                    verdi = (if (prefiks) KlageEventTilOppgaveMapper.KLAGE_PREFIX else "") + kodeverdi.kode,
                    visningsnavn = KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + kodeverdi.navn,
                    synlighet = synlighet,
                    beskrivelse = beskrivelse
                )
            }.sortedBy { it.visningsnavn }
    }
}

object KodeverkSynlighetRegler {
    fun behandlingType(behandlingType: BehandlingType): Synlighet {
        return when (behandlingType) {
            BehandlingType.ANKE -> Synlighet.SKJULT
            BehandlingType.FORSTEGANGSSOKNAD,
            BehandlingType.REVURDERING,
            BehandlingType.REVURDERING_TILBAKEKREVING -> Synlighet.OVER_STREKEN

            else -> Synlighet.UNDER_STREKEN
        }
    }

    fun søknadÅrsak(søknadÅrsak: UtvidetSøknadÅrsak): Synlighet {
        return when (søknadÅrsak) {
            else -> Synlighet.OVER_STREKEN
        }
    }


    fun ytelseType(ytelseType: FagsakYtelseType): Pair<Synlighet, Int?> {
        return when (ytelseType) {
            FagsakYtelseType.FRISINN,
            FagsakYtelseType.UNGDOMSYTELSE,
            FagsakYtelseType.OMSORGSDAGER -> Synlighet.SKJULT to null

            FagsakYtelseType.UKJENT -> Synlighet.OVER_STREKEN to -1

            else -> Synlighet.OVER_STREKEN to null
        }
    }
}
