package no.nav.k9.los.infrastruktur.abac

import java.util.*

internal data class GrupperForOmråde(
    val saksbehandler: UUID?,
    val veileder: UUID?,
    val oppgavestyrer: UUID?,
    val drift: UUID?,
    val kode6: UUID?,
)

