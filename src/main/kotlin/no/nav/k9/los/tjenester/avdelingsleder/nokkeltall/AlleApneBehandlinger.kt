package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType

data class AlleApneBehandlinger(
    val behandlingType: BehandlingType,
    val antall: Int
)
