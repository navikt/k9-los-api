package no.nav.k9.los.tjenester.saksbehandler.merknad

import java.time.LocalDateTime

data class Merknad(
    val oppgaveKoder: List<String>,
    val oppgaveIder: List<Long>,
    val saksbehandler: String?,
    val opprettet: LocalDateTime,
    var slettet: Boolean = false,
    var sistEndret: LocalDateTime? = LocalDateTime.now(),
    var merknadKoder: List<String> = emptyList(),
    var fritekst: String? = null
) {
    var id: Long? = null
        set(value) {
            check(this.id == null) { "Forsøker å overskrive satt id" }
            field = value
        }

    fun oppdater(merknadKoder: List<String>, fritekst: String?) {
        this.merknadKoder = merknadKoder
        this.fritekst = fritekst
        this.sistEndret = LocalDateTime.now()
    }

    fun slett() {
        slettet = true
        sistEndret = LocalDateTime.now()
    }
}