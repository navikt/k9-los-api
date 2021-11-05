package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.modell.BehandlingType

data class AlleApneBehandlinger(
    val behandlingType: BehandlingType,
    val antall: Int
)
