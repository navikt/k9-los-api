package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDate

data class AlleOppgaverNyeOgFerdigstilteDto (
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    val nye: Int,
    val ferdigstilte: Int
)
