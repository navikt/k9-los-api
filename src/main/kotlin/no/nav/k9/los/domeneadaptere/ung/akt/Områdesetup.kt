package no.nav.k9.los.domeneadaptere.ung.akt

import no.nav.k9.los.domeneadaptere.ung.akt.oppgavedefinisjon.AktivitetspengerFeltdefinisjoner
import no.nav.k9.los.domeneadaptere.ung.akt.oppgavedefinisjon.AktivitetspengerOppgaver
import no.nav.k9.los.kodeverk.AktFagsystem
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkVerdiDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.kodeverk.BehandlendeEnhet
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste
import no.nav.ung.kodeverk.api.Kodeverdi
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingStegType
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.BehandlingÅrsakType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon

class Områdesetup(
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
) {
    private val område = Områder.AKTIVITETSPENGER.eksternId

    fun setup() {
        områdeRepository.lagre(område)
        feltdefinisjonTjeneste.oppdater(oppdaterKodeverk())
        feltdefinisjonTjeneste.oppdater(AktivitetspengerFeltdefinisjoner.lagFeltdefinisjoner())
        oppgavetypeTjeneste.oppdater(AktivitetspengerOppgaver.lagOppgaveDefinisjon())
    }

    private fun oppdaterKodeverk(): List<KodeverkDto> {
        return listOf(
            kodeverkBehandlingtype(),
            kodeverkBehandlingsstatus(),
            kodeverkBehandlingssteg(),
            kodeverkBehandlingsårsak(),
            kodeverkFagsystem(),
            kodeverkResultattype(),
            kodeverkYtelsetype(),
            kodeverkBehandlendeEnhet(),
            kodeverkAksjonspunkt()
        )
    }

    private fun kodeverkBehandlingtype(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = BehandlingType::class.java.simpleName,
            beskrivelse = "Behandlingstype",
            uttømmende = true,
            verdier = BehandlingType.entries
                .map { behandlingType ->
                    val (gruppering, synlighet, rekkefølge) = when (behandlingType) {
                        BehandlingType.FØRSTEGANGSSØKNAD,
                        BehandlingType.REVURDERING -> Triple("Ordinærbehandling", Synlighet.OVER_STREKEN, 1)

                        BehandlingType.KLAGE,
                        BehandlingType.ANKE -> Triple("Klage", Synlighet.OVER_STREKEN, 2)

                        BehandlingType.TILBAKEKREVING,
                        BehandlingType.REVURDERING_TILBAKEKREVING -> Triple("Tilbakekreving", Synlighet.OVER_STREKEN, 3)

                        else -> Triple("Øvrige behandlingstyper", Synlighet.UNDER_STREKEN, 5)
                    }

                    KodeverkVerdiDto(
                        verdi = behandlingType.kode,
                        visningsnavn = behandlingType.navn,
                        synlighet = synlighet,
                        gruppering = gruppering,
                        rekkefølge = rekkefølge
                    )
                }
        )
    }

    private fun kodeverkBehandlingsstatus(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = BehandlingStatus::class.java.simpleName,
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingStatus.entries.lagDto()
        )
    }

    private fun kodeverkBehandlingssteg(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = BehandlingStegType::class.java.simpleName,
            beskrivelse = null,
            uttømmende = false,
            verdier = BehandlingStegType.entries.lagDto()
        )
    }

    private fun kodeverkBehandlingsårsak(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = BehandlingÅrsakType::class.java.simpleName,
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingÅrsakType.entries.lagDto()
        )
    }

    private fun kodeverkFagsystem(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = AktFagsystem::class.java.simpleName,
            beskrivelse = null,
            uttømmende = true,
            verdier = AktFagsystem.entries
                .map { fagsystem ->
                    KodeverkVerdiDto(
                        verdi = fagsystem.kode,
                        visningsnavn = fagsystem.navn,
                        synlighet = Synlighet.OVER_STREKEN
                    )
                }
                .sortedBy { it.visningsnavn }
        )
    }

    private fun kodeverkResultattype(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = BehandlingResultatType::class.java.simpleName,
            beskrivelse = null,
            uttømmende = true,
            verdier = BehandlingResultatType.entries.lagDto()
        )
    }

    private fun kodeverkYtelsetype(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = FagsakYtelseType::class.java.simpleName,
            beskrivelse = null,
            uttømmende = true,
            verdier = FagsakYtelseType.entries.lagDto()
        )
    }

    private fun kodeverkBehandlendeEnhet(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = "behandlendeEnhet",
            beskrivelse = null,
            uttømmende = false,
            verdier = BehandlendeEnhet.entries
                .map { enhet ->
                    KodeverkVerdiDto(
                        verdi = enhet.kode,
                        visningsnavn = enhet.navn,
                        synlighet = Synlighet.OVER_STREKEN
                    )
                }
                .sortedBy { it.visningsnavn }
        )
    }

    private fun kodeverkAksjonspunkt(): KodeverkDto {
        return KodeverkDto(
            område = område,
            eksternId = "Aksjonspunkt",
            beskrivelse = null,
            uttømmende = false,
            verdier = AksjonspunktDefinisjon.entries.lagDto()
        )
    }

    private fun <T : Kodeverdi> Collection<T>.lagDto() =
        map { kodeverdi ->
            KodeverkVerdiDto(
                verdi = kodeverdi.kode,
                visningsnavn = kodeverdi.navn,
                synlighet = Synlighet.OVER_STREKEN
            )
        }.sortedBy { it.visningsnavn }

}