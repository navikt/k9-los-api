package no.nav.k9.los.nyoppgavestyring.kodeverk

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.los.domene.lager.oppgave.Kodeverdi
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.Venteårsak

class HentKodeverkTjeneste() {

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
        koder["Aksjonspunkt"] = kodelisteAksjonspunktK9sak() + kodelisteAksjonspunktK9klage()
        return koder
    }

    private fun kodelisteAksjonspunktK9sak(): List<AksjonspunktKodeverkDto> {
        val k9SakAp = AksjonspunktDefinisjon.entries
        return k9SakAp.map { ap ->
            when {
                ap.aksjonspunktType == AksjonspunktType.AUTOPUNKT -> {
                    AksjonspunktKodeverkDto(
                        kode = ap.kode,
                        navn = ap.navn,
                        kodeverk = OppgaveKodeGruppe.AUTOPUNKT.navn
                    )
                }

                AksjonspunktDefinisjon.entries.contains(AksjonspunktDefinisjon.fraKode(ap.kode)) -> {
                    AksjonspunktKodeverkDto(
                        kode = ap.kode,
                        navn = ap.navn,
                        kodeverk = ap.behandlingSteg.navn
                    )
                }

                else -> {
                    AksjonspunktKodeverkDto(
                        kode = ap.kode,
                        navn = ap.navn,
                        kodeverk = "Uspesifisert"
                    )
                }
            }
        }
    }

    private fun kodelisteAksjonspunktK9klage(): List<AksjonspunktKodeverkDto> {
        val k9KlageAp = no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.entries
        return k9KlageAp.map { ap ->
            when {
                ap.aksjonspunktType == no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktType.AUTOPUNKT -> {
                    AksjonspunktKodeverkDto(
                        kode = "KLAGE" + ap.kode,
                        navn = ap.navn,
                        kodeverk = OppgaveKodeGruppe.AUTOPUNKT.navn
                    )
                }

                AksjonspunktDefinisjon.entries.contains(AksjonspunktDefinisjon.fraKode(ap.kode)) -> {
                    AksjonspunktKodeverkDto(
                        kode = "KLAGE" + ap.kode,
                        navn = ap.navn,
                        kodeverk = ap.behandlingSteg.navn
                    )
                }

                else -> {
                    AksjonspunktKodeverkDto(
                        kode = "KLAGE" + ap.kode,
                        navn = ap.navn,
                        kodeverk = "Uspesifisert"
                    )
                }
            }
        }
    }
}

class AksjonspunktKodeverkDto(
    override val kode: String,
    override val navn: String,
    override val kodeverk: String,
): Kodeverdi