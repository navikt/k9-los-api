package no.nav.k9.aksjonspunktbehandling

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.IModell
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringAksjonspunktHendelse
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringDokumentHendelse
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringHendelse
import org.slf4j.LoggerFactory


class K9sakEventHandlerV2(
    val azureGraphService: IAzureGraphService
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9sakEventHandlerV2::class.java)

    suspend fun prosesser(hendelse: ProduksjonsstyringHendelse) {
        when (hendelse) {
            is ProduksjonsstyringDokumentHendelse -> håndterNyttDokument(hendelse)
            is ProduksjonsstyringAksjonspunktHendelse -> håndterNyttAksjonspunkt(hendelse)
            else -> throw UnsupportedOperationException("Mottok eventtype som ikke er støttet ${hendelse.tryggToString()}")
        }
    }

    private suspend fun håndterNyttAksjonspunkt(aksjonspunkthendelse: ProduksjonsstyringAksjonspunktHendelse) {
        log.warn("AKSJONSPUNKTHENDELSE er ikke implementert ${aksjonspunkthendelse.aksjonspunktTilstander.joinToString(", ") { it.toString() }}")
        val aksjonspunkterMedBehandlendeEnhet = aksjonspunkthendelse.aksjonspunktTilstander.associateBy { it!! }
            .mapValues { (_, v) ->
                v.ansvarligSaksbehandler?.let { azureGraphService.hentEnhetForBrukerMedSystemToken(it) }
            }
    }

    private fun håndterNyttDokument(dokumenthendelse: ProduksjonsstyringDokumentHendelse) {
        log.warn("DOKUMENTHENDELSE er ikke implementert ${dokumenthendelse.kravdokumenter.joinToString(", ") { it.toString() }}")
    }

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        TODO("Not yet implemented")
    }
}
