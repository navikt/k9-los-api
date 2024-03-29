package no.nav.k9.los.tjenester.fagsak


import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import java.time.LocalDateTime

data class FagsakDto (
    val fagsystem: Fagsystem,
    val saksnummer: String,
    val sakstype: FagsakYtelseType,
    val opprettet: LocalDateTime,
    val aktiv: Boolean,
    val behandlingStatus: BehandlingStatus?,
    val behandlingId: Long?,
    val journalpostId: String?,
)
