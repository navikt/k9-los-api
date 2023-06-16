package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.KodeverkDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.KodeverkVerdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    }

    private fun kodeverkAksjonspunkt() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Aksjonspunkt",
            beskrivelse = null,
            uttømmende = false,
            verdier = AksjonspunktDefinisjon.values().filterNot { it == AksjonspunktDefinisjon.UNDEFINED }.map { apDefinisjon ->
                KodeverkVerdiDto(
                    verdi = apDefinisjon.kode,
                    visningsnavn = apDefinisjon.navn,
                    beskrivelse = null,
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkFagsystem() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Fagsystem",
            beskrivelse = null,
            uttømmende = true,
            verdier = Fagsystem.values().map { fagsystem ->
                KodeverkVerdiDto(
                    verdi = fagsystem.kode,
                    visningsnavn = fagsystem.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkResultattype() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Resultattype",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingResultatType.values().map { resultattype ->
                KodeverkVerdiDto(
                    verdi = resultattype.kode,
                    visningsnavn = resultattype.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkYtelsetype() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Ytelsetype",
            beskrivelse = null,
            uttømmende = true,
            verdier = FagsakYtelseType.values().map { ytelsetype ->
                KodeverkVerdiDto(
                    verdi = ytelsetype.kode,
                    visningsnavn = ytelsetype.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkBehandlingstatus() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Behandlingsstatus",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingStatus.values().map { behandlingstatus ->
                KodeverkVerdiDto(
                    verdi = behandlingstatus.kode,
                    visningsnavn = behandlingstatus.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkBehandlingtype() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Behandlingtype",
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingType.values().map { behandlingtype ->
                KodeverkVerdiDto(
                    verdi = behandlingtype.kode,
                    visningsnavn = behandlingtype.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkVenteårsak() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Venteårsak",
            beskrivelse = null,
            uttømmende = true,
            verdier = Venteårsak.values().map { venteårsak ->
                KodeverkVerdiDto(
                    verdi = venteårsak.kode,
                    visningsnavn = venteårsak.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }

    private fun kodeverkBehandlingssteg() {
        val kodeverkDto = KodeverkDto(
            område = område,
            eksternId = "Behandlingssteg",
            beskrivelse = null,
            uttømmende = false,
            verdier = BehandlingStegType.values().map { verdi ->
                KodeverkVerdiDto(
                    verdi = verdi.kode,
                    visningsnavn = verdi.navn,
                    beskrivelse = null
                )
            }
        )
        feltdefinisjonTjeneste.oppdater(kodeverkDto)
    }
}