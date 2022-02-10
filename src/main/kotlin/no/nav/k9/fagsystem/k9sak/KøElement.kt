package no.nav.k9.fagsystem.k9sak

import no.nav.k9.kodeverk.vilkår.Utfall
import java.util.*

class KøElement(
    val eksternReferanse: UUID,
    val utfall: Utfall
)

enum class Utfall {
    INKLUDERT,
    EKSKLUDERT,
}