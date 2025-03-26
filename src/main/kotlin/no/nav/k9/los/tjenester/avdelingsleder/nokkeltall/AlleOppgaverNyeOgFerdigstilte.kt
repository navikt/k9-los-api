package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDate

data class AlleOppgaverNyeOgFerdigstilte(
    var fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    val nye: MutableSet<String> = mutableSetOf(),
    val ferdigstilte: MutableSet<String> = mutableSetOf(),
    val ferdigstilteSaksbehandler: MutableSet<String> = mutableSetOf()
)
