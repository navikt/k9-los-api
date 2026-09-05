package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.time.LocalDateTime

class Saksbehandler(
    var id: Long?,
    var navident: String?,
    var navn: String?,
    var epost: String,
    var enhet: String?,
    var områder: List<Områder>,
    val kode6: Boolean,
    val sistOppdatert: LocalDateTime? = null,
) {
    override fun toString(): String {
        return navident ?: ""
    }
}
