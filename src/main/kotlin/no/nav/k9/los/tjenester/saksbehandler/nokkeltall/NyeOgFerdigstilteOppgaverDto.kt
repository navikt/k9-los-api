package no.nav.k9.los.tjenester.saksbehandler.nokkeltall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDate

data class NyeOgFerdigstilteOppgaverDto(
    val behandlingType: BehandlingType,
    val fagsakYtelseType: FagsakYtelseType,
    val dato: LocalDate,
    val antallNye: Int,
    val antallFerdigstilte: Int,
    val antallFerdigstilteMine: Int = 0
)
