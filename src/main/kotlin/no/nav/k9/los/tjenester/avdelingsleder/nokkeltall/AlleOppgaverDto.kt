package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType

data class AlleOppgaverDto(
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val tilBehandling: Boolean,
    val antall: Int
)
