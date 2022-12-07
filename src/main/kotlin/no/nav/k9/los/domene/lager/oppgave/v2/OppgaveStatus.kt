package no.nav.k9.los.domene.lager.oppgave.v2

import java.util.*

enum class OppgaveStatus(val kode: String) {
    OPPRETTET("OPPRETTET"),
    UNDER_BEHANDLING("UNDER_BEHANDLING"),
    AVBRUTT("AVBRUTT"),
    FERDIGSTILT("FERDIGSTILT"),
    FEILREGISTRERT("FEILREGISTRERT");

    companion object {
        val aktivOppgaveKoder = EnumSet.of(OPPRETTET, UNDER_BEHANDLING)
    }

    fun erAktiv(): Boolean {
        return aktivOppgaveKoder.contains(this)
    }

    fun erFerdigstilt(): Boolean {
        return this == FERDIGSTILT
    }
}