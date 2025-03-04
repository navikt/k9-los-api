package no.nav.k9.los.tjenester.saksbehandler.saksliste

import no.nav.k9.los.domene.modell.Saksbehandler

data class SaksbehandlerDto(
    val id: Long?,
    val brukerIdent: String?,
    val navn: String?,
    val epost: String,
    val enhet: String?,
) {
    constructor(saksbehandler: Saksbehandler): this(
        id = saksbehandler.id,
        brukerIdent = saksbehandler.brukerIdent,
        navn = saksbehandler.navn,
        epost = saksbehandler.epost,
        enhet = saksbehandler.enhet,
    )
}
