package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import java.time.LocalDate

data class AlleOppgaverNyeOgFerdigstilte(
    var fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    val kilde: Fagsystem,
    val nye: MutableSet<String> = mutableSetOf(),
    val ferdigstilte: MutableSet<String> = mutableSetOf(),
    val ferdigstilteSaksbehandler: MutableSet<String> = mutableSetOf()
)
