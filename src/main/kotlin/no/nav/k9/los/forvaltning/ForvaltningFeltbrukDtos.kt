package no.nav.k9.los.forvaltning

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

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
    val område: Områder?,
    val kode: String,
    val antallOppgavekøer: Int,
    val antallLagredeSøk: Int
)
