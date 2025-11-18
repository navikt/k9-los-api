package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.produksjonsstyring.UtvidetSøknadÅrsak
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
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
            K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("Oppretter/oppdaterer feltdefinisjoner for område $område")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    @WithSpan
    private fun ajourholdOppgavetype(oppgavedefinisjon: String, frontendUrl: String) {
        val oppgavetyperDto = LosObjectMapper.instance.readValue(
            K9TilbakeTilLosAdapterTjeneste::class.java.getResource(oppgavedefinisjon)!!
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
        AksjonspunktDefinisjon.entries.filterNot { it == AksjonspunktDefinisjon.UNDEFINED }.map { apDefinisjon ->
            KodeverkVerdiDto(
                verdi = apDefinisjon.kode,
                visningsnavn = apDefinisjon.navn,
                beskrivelse = null,
            )
        }

    private fun aksjonspunktVerdierK9Klage() =
        no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.entries
            .filterNot { it == no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.UNDEFINED }
            .map { apDefinisjon ->
                KodeverkVerdiDto(
                    verdi = KlageEventTilOppgaveMapper.KLAGE_PREFIX + apDefinisjon.kode,
                    visningsnavn = KlageEventTilOppgaveMapper.KLAGE_PREFIX_VISNING + apDefinisjon.navn,
                    beskrivelse = null,
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
            verdier = BehandlingType.entries.lagDto(beskrivelse = null, KodeverkSynlighetRegler::behandlingType)
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkVenteårsak() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Venteårsak",
            beskrivelse = null,
            uttømmende = true,
            verdier = no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak.entries.lagK9Dto(beskrivelse = null) + no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.Venteårsak.entries.lageK9KlageDto(beskrivelse = null),
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
        val k9sakKodeverk = no.nav.k9.kodeverk.behandling.BehandlingÅrsakType.entries.lagK9Dto(
            beskrivelse = null,
            KodeverkSynlighetRegler::behandlingsårsak
        )
        val k9klageKodeverk = no.nav.k9.klage.kodeverk.behandling.BehandlingÅrsakType.entries.lageK9KlageDto(
            beskrivelse = null,
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
        synlighet: (T) -> KodeverkSynlighet = { KodeverkSynlighet.SYNLIG_FAVORITT }
    ): List<KodeverkVerdiDto> {
        return associateWith { synlighet(it) }
            .filter { (_, synlighet) -> synlighet != KodeverkSynlighet.SKJULT }
            .map { (kodeverdi, synlighet) ->
                KodeverkVerdiDto(
                    verdi = KlageEventTilOppgaveMapper.KLAGE_PREFIX + kodeverdi.kode,
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
            FagsakYtelseType.UNGDOMSYTELSE -> KodeverkSynlighet.SKJULT

            FagsakYtelseType.UKJENT,
            FagsakYtelseType.OMSORGSDAGER -> KodeverkSynlighet.SYNLIG

            else -> KodeverkSynlighet.SYNLIG_FAVORITT
        }
    }
}

enum class KodeverkSynlighet {
    SKJULT,
    SYNLIG,
    SYNLIG_FAVORITT;
}