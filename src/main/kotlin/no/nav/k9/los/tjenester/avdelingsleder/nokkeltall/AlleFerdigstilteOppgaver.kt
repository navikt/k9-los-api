package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import java.time.LocalDate

data class AlleFerdigstilteOppgaver(
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    var antall: Int
)
