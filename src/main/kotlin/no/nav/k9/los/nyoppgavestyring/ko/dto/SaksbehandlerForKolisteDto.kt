package no.nav.k9.los.nyoppgavestyring.ko.dto

data class SaksbehandlerForKolisteDto(
    val id: Long,
    val epost: String,
    val navn: String,
    val enhet: String,
) {
    constructor(saksbehandler: no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler) : this(
        id = saksbehandler.id!!,
        epost = saksbehandler.epost,
        navn = saksbehandler.navn ?: "",
        enhet = saksbehandler.enhet ?: ""
    )
}