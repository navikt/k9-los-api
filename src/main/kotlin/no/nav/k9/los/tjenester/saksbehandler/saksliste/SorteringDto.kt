package no.nav.k9.los.tjenester.saksbehandler.saksliste

import no.nav.k9.los.domene.modell.KøSortering
import java.time.LocalDate

data class SorteringDto(
    val sorteringType: KøSortering,
    var fomDato: LocalDate?,
    var tomDato: LocalDate?
)
