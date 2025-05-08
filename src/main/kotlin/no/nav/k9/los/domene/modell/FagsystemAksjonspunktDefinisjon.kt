package no.nav.k9.los.domene.modell

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.AksjonspunktDefinisjonPunsj
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

data class FagsystemAksjonspunktDefinisjon(
    val fagsystem: Fagsystem,
    val aksonspunktKode: String,
    val defaultTotrinnBehandling: Boolean,
    val erAutopunkt: Boolean = false,
    val tilBeslutter : Boolean = false
)

object FagsystemAksjonspunktDefinisjoner {

    private val alleAksjonspunktdefinisjoner = genererAlle()

    fun fraKode(fagsystem: Fagsystem, kode : String) : FagsystemAksjonspunktDefinisjon{
        return alleAksjonspunktdefinisjoner.first { it.fagsystem == fagsystem && it.aksonspunktKode == kode }
    }

    private fun genererAlle(): List<FagsystemAksjonspunktDefinisjon> {
        val liste = ArrayList<FagsystemAksjonspunktDefinisjon>()

        for (k9sakAksjonspunktDefinisjon in no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.entries) {
            if (k9sakAksjonspunktDefinisjon == no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.UNDEFINED){
                continue //kan ikke legge til da aksjonspunktkode er null
            }
            liste.add(
                FagsystemAksjonspunktDefinisjon(
                    Fagsystem.K9SAK,
                    k9sakAksjonspunktDefinisjon.kode,
                    k9sakAksjonspunktDefinisjon.defaultTotrinnBehandling,
                    k9sakAksjonspunktDefinisjon.erAutopunkt(),
                )
            )
        }
        for (k9klageAksjonspunktDefinisjon in no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.entries) {
            if (k9klageAksjonspunktDefinisjon == no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.UNDEFINED){
                continue //kan ikke legge til da aksjonspunktkode er null
            }
            liste.add(
                FagsystemAksjonspunktDefinisjon(
                    Fagsystem.K9KLAGE,
                    k9klageAksjonspunktDefinisjon.kode,
                    k9klageAksjonspunktDefinisjon.defaultTotrinnBehandling,
                    k9klageAksjonspunktDefinisjon.erAutopunkt()
                )
            )
        }

        for (k9tilbakeAksjonspunktDefinisjon in AksjonspunktDefinisjonK9Tilbake.alle()) {
            liste.add(
                FagsystemAksjonspunktDefinisjon(
                    Fagsystem.K9TILBAKE,
                    k9tilbakeAksjonspunktDefinisjon.kode,
                    k9tilbakeAksjonspunktDefinisjon.totrinn,
                    k9tilbakeAksjonspunktDefinisjon.erAutopunkt
                )
            )
        }

        for (punsjAksjonspunktDefinisjon in AksjonspunktDefinisjonPunsj.alle()) {
            val totrinn = false
            liste.add(
                FagsystemAksjonspunktDefinisjon(
                    Fagsystem.PUNSJ,
                    punsjAksjonspunktDefinisjon.kode,
                    totrinn,
                    punsjAksjonspunktDefinisjon.erAutopunkt
                )
            )
        }

        return liste
    }
}
