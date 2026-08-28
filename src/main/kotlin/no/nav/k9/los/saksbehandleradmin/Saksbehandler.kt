package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class Saksbehandler(
    var id: Long?,
    var navident: String?,
    var navn: String?,
    var epost: String,
    var enhet: String?,
    var områder: List<Områder>,
    val kode6: Boolean
) {
    override fun toString(): String {
        return navident ?: ""
    }
}