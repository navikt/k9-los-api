package no.nav.k9.los.saksbehandleradmin

import java.time.LocalDateTime

class Saksbehandler(
    val id: Long,
    val navident: String?,
    val navn: String?,
    val epost: String,
    val enhet: String?,
    val skjermet: Boolean,
    val sistOppdatert: LocalDateTime? = null
) {
    override fun toString(): String {
        return navident ?: ""
    }
}
