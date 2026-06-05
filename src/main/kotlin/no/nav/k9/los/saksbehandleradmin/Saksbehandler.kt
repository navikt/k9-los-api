package no.nav.k9.los.saksbehandleradmin

class Saksbehandler(
    var id: Long?,
    var navident: String?,
    var navn: String?,
    var epost: String,
    var enhet: String?
) {
    override fun toString(): String {
        return navident ?: ""
    }
}