package no.nav.k9.los.tjenester.saksbehandler.nokkeltall

import no.nav.k9.los.domene.modell.BehandlingType
import java.time.LocalDate

data class NyeOgFerdigstilteOppgaver(
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    val nye: MutableSet<String> = mutableSetOf(),
    val ferdigstilte: MutableSet<String> = mutableSetOf()
) {
    fun leggTilNy(uuid: String) {
        nye.add(uuid)
    }
    fun leggTilFerdigstilt(uuid: String) {
        ferdigstilte.add(uuid)
    }
}
