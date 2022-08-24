package no.nav.k9.domene.lager.oppgave.v3

import java.time.LocalDateTime

class OppgaveV3(
    val id: Long?,
    val eksternId: String,
    val oppgavetypeId: Long,
    val versjon: Int,
    val aktiv: Boolean,
    val opprettet: LocalDateTime,
    val fullført: LocalDateTime?
)