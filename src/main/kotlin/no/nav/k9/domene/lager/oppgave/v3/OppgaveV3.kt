package no.nav.k9.domene.lager.oppgave.v3

import java.time.LocalDateTime

class OppgaveV3(
    var id: Long?,
    var aktiv: Boolean,
    var reservasjonsNøkkel: String,
    var opprettet: LocalDateTime, // Hører disse to hjemme i OppgaveFelt/OppgaveFeltVerdi?
    var avsluttet: LocalDateTime?
)