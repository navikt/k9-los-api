package no.nav.k9.tjenester.saksbehandler.saksliste

import no.nav.k9.domene.modell.KøSortering
import java.time.LocalDate

data class SorteringDto(
    val sorteringType: KøSortering,
    var fomDato: LocalDate?,
    var tomDato: LocalDate?
)
