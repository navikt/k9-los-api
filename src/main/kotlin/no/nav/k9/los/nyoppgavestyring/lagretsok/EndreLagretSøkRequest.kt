package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery

data class EndreLagretSøkRequest(
    val id: Long,
    val versjon: Long,
    val tittel: String,
    val beskrivelse: String,
    val query: OppgaveQuery
)
