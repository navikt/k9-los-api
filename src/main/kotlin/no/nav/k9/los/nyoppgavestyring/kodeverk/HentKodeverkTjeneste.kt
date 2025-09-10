package no.nav.k9.los.nyoppgavestyring.kodeverk

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.los.domene.lager.oppgave.Kodeverdi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.Venteårsak

class HentKodeverkTjeneste(
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val områdeSetup: OmrådeSetup,
) {
    private var aksjonspunktKodeverdier: List<AksjonspunktKodeverkDto> = emptyList()

    fun hentGruppertKodeliste(): MutableMap<String, Collection<Kodeverdi>> {
        val koder = mutableMapOf<String, Collection<Kodeverdi>>()
        koder[BehandlingType::class.java.simpleName] = BehandlingType.entries
        koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.entries
        koder[KøSortering::class.java.simpleName] = KøSortering.entries
        koder[FagsakStatus::class.java.simpleName] = FagsakStatus.entries
        koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.entries
        koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.entries
        koder[Venteårsak::class.java.simpleName] = Venteårsak.entries
        koder[KøKriterierType::class.java.simpleName] = KøKriterierType.entries
            .filterNot { it == KøKriterierType.BEHANDLINGTYPE } // ikke i bruk foreløpig
        koder[MerknadType::class.java.simpleName] = MerknadType.entries
            .filterNot { it == MerknadType.VANSKELIG } // ikke støttet foreløpig
        koder[OppgaveKode::class.java.simpleName] = OppgaveKode.entries
        koder["Aksjonspunkt"] = kodelisteAksjonspunkt()
        return koder
    }

    private fun kodelisteAksjonspunktK9sak(): List<AksjonspunktKodeverkDto> {
        val k9SakAp = AksjonspunktDefinisjon.entries
        val kodeverkDtos = k9SakAp.map { ap ->
            when {
                ap.aksjonspunktType == AksjonspunktType.AUTOPUNKT -> {
                    AksjonspunktKodeverkDto(
                        kode = ap.kode,
                        navn = ap.navn,
                        gruppering = OppgaveKodeGruppe.AUTOPUNKT
                    )
                }

                ap. -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.INNLEDENDE_BEHANDLING
                    )
                }

                "" -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.OM_BARNET
                    )
                }

                "" -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.MANGLER_INNTEKTSMELDING
                    )
                }

                "" -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.BEREGNING
                    )
                }

                "" -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.FLYTTESAKER
                    )
                }

                "" -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.FATTE_VEDTAK
                    )
                }

                "" -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.UTTAK
                    )
                }

                else -> {
                    AksjonspunktKodeverkDto(
                        kode = kodeverkVerdi.verdi,
                        navn = kodeverkVerdi.visningsnavn,
                        gruppering = OppgaveKodeGruppe.USPESIFISERT
                    )
                }
            }

        }
    }
}

class AksjonspunktKodeverkDto(
    private val kode: String,
    private val navn: String,
    private val gruppering: OppgaveKodeGruppe,
)