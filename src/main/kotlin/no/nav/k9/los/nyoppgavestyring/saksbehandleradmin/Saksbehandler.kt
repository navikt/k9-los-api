package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

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