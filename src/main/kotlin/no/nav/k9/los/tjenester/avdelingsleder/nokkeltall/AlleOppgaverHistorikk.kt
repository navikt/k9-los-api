package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import java.time.LocalDate


data class AlleOppgaverHistorikk(
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    val antall: Int
)
