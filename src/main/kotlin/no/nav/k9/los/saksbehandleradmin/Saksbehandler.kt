package no.nav.k9.los.saksbehandleradmin

import java.time.LocalDateTime

class Saksbehandler(
    var id: Long?,
    var navident: String?,
    var navn: String?,
    var epost: String,
    var enhet: String?,
    val sistOppdatert: LocalDateTime? = null
) {
    override fun toString(): String {
        return navident ?: ""
    }
}
