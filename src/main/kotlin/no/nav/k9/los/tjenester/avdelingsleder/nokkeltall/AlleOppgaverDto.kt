package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType

data class AlleOppgaverDto(
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val tilBehandling: Boolean,
    val antall: Int
)
