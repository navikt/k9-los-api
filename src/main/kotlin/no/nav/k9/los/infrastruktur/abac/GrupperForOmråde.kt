package no.nav.k9.los.infrastruktur.abac

import java.util.*

internal sealed class GrupperForOmråde {
    abstract val oppgavestyrer: UUID?
    abstract val kode6: UUID?

    abstract fun girBasisTilgang(grupper: Set<UUID>): Boolean
    abstract fun girReserveringstilgang(grupper: Set<UUID>): Boolean
}

internal data class K9Grupper(
    val saksbehandler: UUID?,
    val veileder: UUID?,
    override val oppgavestyrer: UUID?,
    override val kode6: UUID?,
) : GrupperForOmråde() {
    override fun girBasisTilgang(grupper: Set<UUID>) =
        saksbehandler in grupper || veileder in grupper

    override fun girReserveringstilgang(grupper: Set<UUID>) =
        saksbehandler in grupper
}

internal data class AktivitetspengerGrupper(
    val saksbehandlerNavkontor: UUID?,
    val saksbehandlerNay: UUID?,
    override val oppgavestyrer: UUID?,
    override val kode6: UUID?,
) : GrupperForOmråde() {
    override fun girBasisTilgang(grupper: Set<UUID>) =
        saksbehandlerNavkontor in grupper || saksbehandlerNay in grupper

    override fun girReserveringstilgang(grupper: Set<UUID>) =
        saksbehandlerNavkontor in grupper || saksbehandlerNay in grupper
}
