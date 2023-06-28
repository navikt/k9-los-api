package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

class ReservasjonV3Endring(
    val id: Long? = null,
    val annullertReservasjonId: Long,
    val nyReservasjonId: Long?,
    val endretAv: Long,
)
