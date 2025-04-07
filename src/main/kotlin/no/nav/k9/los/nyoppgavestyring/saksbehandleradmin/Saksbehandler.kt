package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import java.util.UUID

class Saksbehandler(
    var id: Long?,
    var brukerIdent: String?,
    var navn: String?,
    var epost: String,
    var reservasjoner: MutableSet<UUID> = mutableSetOf(),
    var enhet: String?
) {
    override fun toString(): String {
        return brukerIdent ?: ""
    }
}