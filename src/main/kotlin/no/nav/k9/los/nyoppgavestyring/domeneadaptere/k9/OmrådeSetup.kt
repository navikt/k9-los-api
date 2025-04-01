package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.produksjonsstyring.UtvidetSøknadÅrsak
import no.nav.k9.los.domene.lager.oppgave.Kodeverdi
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.EventTilDtoMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.KodeverkDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.KodeverkVerdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.query.db.Spørringstrategi
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import no.nav.k9.kodeverk.api.Kodeverdi as KodeverdiK9Sak

class OmrådeSetup(
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
) {
    private val log: Logger = LoggerFactory.getLogger(OmrådeSetup::class.java)
    private val område: String = "K9"

    fun setup() {
        opprettOmråde()
        oppdaterKodeverk()
        oppdaterFeltdefinisjoner()
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
        kodeverkSpørringstrategi()
    }

    private fun kodeverkSpørringstrategi() {
        feltdefinisjonTjeneste.oppdater(
            KodeverkDto("K9", "spørringstrategi", "Spørringstrategi", true, Spørringstrategi.entries.map {
                KodeverkVerdiDto(it.name, it.navn, false)
            })
        )
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
                    verdi = EventTilDtoMapper.KLAGE_PREFIX + apDefinisjon.kode,
                    visningsnavn = EventTilDtoMapper.KLAGE_PREFIX_VISNING + apDefinisjon.navn,
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
            verdier = Venteårsak.entries.lagK9Dto(beskrivelse = null)
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
                    verdi = EventTilDtoMapper.KLAGE_PREFIX + kodeverdi.kode,
                    visningsnavn = EventTilDtoMapper.KLAGE_PREFIX_VISNING + kodeverdi.navn,
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

            FagsakYtelseType.OLP,
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