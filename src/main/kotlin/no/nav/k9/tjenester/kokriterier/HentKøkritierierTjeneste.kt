package no.nav.k9.tjenester.kokriterier

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon

class HentKøkritierierTjeneste {

    fun hentKøkriterier(): KøkriterierDto {
        val aksjonspunktDefinisjoner = AksjonspunktDefinisjon.values()
        return KøkriterierDto(
            område = "k9",
            system = "k9-sak",
            oppgaver = aksjonspunktDefinisjoner
                .filter { !it.kode.isNullOrEmpty() }
                .map { Oppgave(it.kode, it.navn) }
        )
    }

}