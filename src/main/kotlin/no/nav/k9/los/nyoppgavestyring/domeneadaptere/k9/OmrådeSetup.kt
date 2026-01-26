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

    private fun aksjonspunktVerdierK9Sak() =
        AksjonspunktDefinisjon.entries
            .filterNot { it == AksjonspunktDefinisjon.UNDEFINED }
            .map { apDefinisjon ->
                val (gruppering, favoritt) = grupperingK9Sak(apDefinisjon)
                KodeverkVerdiDto(
                    verdi = apDefinisjon.kode,
                    visningsnavn = apDefinisjon.kode + " - " + apDefinisjon.navn,
                    beskrivelse = null,
                    gruppering = gruppering,
                    favoritt = favoritt
                )
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
                val (gruppering, favoritt) = grupperingK9Klage(apDefinisjon)
                KodeverkVerdiDto(
                    verdi = KlageEventTilOppgaveMapper.KLAGE_PREFIX + apDefinisjon.kode,
                    visningsnavn = apDefinisjon.kode + " - " + KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + apDefinisjon.navn,
                    beskrivelse = null,
                    gruppering = gruppering,
                    favoritt = favoritt
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
            eksternId = "Behandlingstype",
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

            BehandlingType.KLAGE,
            BehandlingType.ANKE -> "Klage" to true

            BehandlingType.TILBAKE,
            BehandlingType.REVURDERING_TILBAKEKREVING -> "Tilbakekreving" to true

            BehandlingType.INNSYN,
            BehandlingType.UNNTAKSBEHANDLING -> "Unntak" to true

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
        val k9sakKodeverk = BehandlingÅrsakType.entries.lagK9Dto(
            beskrivelse = null,
            KodeverkSynlighetRegler::behandlingsårsak
        )
        val k9klageKodeverk = no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.entries.lageK9KlageDto(
            beskrivelse = null,
            prefiks = true,
            KodeverkSynlighetRegler::behandlingsårsak
        )
        val koder = k9sakKodeverk + k9klageKodeverk
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "behandlingsårsak",
            beskrivelse = null,
            uttømmende = true,
            verdier = koder
        )


        feltdefinisjonTjeneste.oppdater(kodeverkDto)
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

    fun behandlingsårsak(søknadÅrsak: BehandlingÅrsakType): KodeverkSynlighet {
        return when (søknadÅrsak) {
            BehandlingÅrsakType.RE_HENDELSE_DØD_BARN,
            BehandlingÅrsakType.RE_HENDELSE_DØD_FORELDER -> KodeverkSynlighet.SYNLIG_FAVORITT

            BehandlingÅrsakType.UDEFINERT -> KodeverkSynlighet.SKJULT
            else -> KodeverkSynlighet.SYNLIG
        }
    }

    fun behandlingsårsak(søknadÅrsak: no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType): KodeverkSynlighet {
        return when (søknadÅrsak) {
            no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.UDEFINERT -> KodeverkSynlighet.SKJULT
            else -> KodeverkSynlighet.SYNLIG
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