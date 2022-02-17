package no.nav.k9.aksjonspunktbehandling

import no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt.ProduksjonsstyringAksjonspunktHendelseKontrakt
import no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt.ProduksjonsstyringDokumentHendelseKontrakt
import no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt.ProduksjonsstyringHendelseKontrakt
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.IModell
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import org.slf4j.LoggerFactory


class K9sakEventHandlerV2(
    val azureGraphService: IAzureGraphService
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9sakEventHandlerV2::class.java)

    suspend fun prosesser(hendelse: ProduksjonsstyringHendelseKontrakt) {
        when (hendelse) {
            is ProduksjonsstyringDokumentHendelseKontrakt -> håndterNyttDokument(hendelse)
            is ProduksjonsstyringAksjonspunktHendelseKontrakt -> håndterNyttAksjonspunkt(hendelse)
            else -> throw UnsupportedOperationException("Mottok eventtype som ikke er støttet ${hendelse.tryggToString()}")
        }
    }

    private suspend fun håndterNyttAksjonspunkt(aksjonspunkthendelse: ProduksjonsstyringAksjonspunktHendelseKontrakt) {
        log.warn("AKSJONSPUNKTHENDELSE er ikke implementert ${aksjonspunkthendelse.aksjonspunktTilstander.joinToString(", ") { it.toString() }}")
        val aksjonspunkterMedBehandlendeEnhet = aksjonspunkthendelse.aksjonspunktTilstander.associateBy { it!! }
            .mapValues { (_, v) ->
                v.ansvarligSaksbehandler?.let { azureGraphService.hentEnhetForBrukerMedSystemToken(it) }
            }
    }

    private fun håndterNyttDokument(dokumenthendelse: ProduksjonsstyringDokumentHendelseKontrakt) {
        log.warn("DOKUMENTHENDELSE er ikke implementert ${dokumenthendelse.kravdokumenter.joinToString(", ") { it.toString() }}")
    }

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        TODO("Not yet implemented")
    }
}
