package no.nav.k9.los.tjenester.saksbehandler.saksliste

import no.nav.k9.los.nyoppgavestyring.kodeverk.KøSortering
import java.time.LocalDate

data class SorteringDto(
    val sorteringType: KøSortering,
    var fomDato: LocalDate?,
    var tomDato: LocalDate?
)
