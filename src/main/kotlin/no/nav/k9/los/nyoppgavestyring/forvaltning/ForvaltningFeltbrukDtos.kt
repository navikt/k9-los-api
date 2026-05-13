package no.nav.k9.los.nyoppgavestyring.forvaltning

data class FeltbrukDetaljerDto(
    val oppgavekøer: List<OppgavekøFeltbrukDto>,
    val lagredeSøk: List<LagretSøkFeltbrukDto>
)

data class OppgavekøFeltbrukDto(
    val id: Long,
    val tittel: String
)

data class LagretSøkFeltbrukDto(
    val id: Long,
    val tittel: String,
    val saksbehandlerEpost: String
)

data class FeltbrukOversiktDto(
    val område: String?,
    val kode: String,
    val antallOppgavekøer: Int,
    val antallLagredeSøk: Int
)
