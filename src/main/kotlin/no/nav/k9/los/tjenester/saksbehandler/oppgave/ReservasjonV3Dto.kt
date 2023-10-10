package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDateTime

data class ReservasjonV3Dto ( //TODO: WIP avklare med Vebjørn hvor mange felter som trengs jfr OppgaveDto som returneres fra gammel fa-oppgave-fra-ko
    val reserverteV3OppgaverEksternId: List<String>,

    // Fjernes når V1 skal vekk
    val reservertV1OppgaveEksternId: String? = null,

    val reservasjonsnøkkel: String,
    val reservertAv: String,
    val kommentar: String,
    val reservertFra: LocalDateTime,
    val reservertTil: LocalDateTime?,
) {
    constructor(reservasjonV3: ReservasjonV3, oppgaver: List<Oppgave>, reservertAv: Saksbehandler) : this (
        reserverteV3OppgaverEksternId = oppgaver.map { it.eksternId },
        reservasjonsnøkkel = reservasjonV3.reservasjonsnøkkel,
        reservertAv = reservertAv.epost,
        kommentar = reservasjonV3.kommentar,
        reservertFra = reservasjonV3.gyldigFra,
        reservertTil = reservasjonV3.gyldigTil
    )

    // Fjernes når V1 skal vekk
    constructor(reservasjonV3: ReservasjonV3, oppgave: String, reservertAv: Saksbehandler) : this (
        reserverteV3OppgaverEksternId = emptyList(),
        reservertV1OppgaveEksternId = oppgave,
        reservasjonsnøkkel = reservasjonV3.reservasjonsnøkkel,
        reservertAv = reservertAv.epost,
        kommentar = reservasjonV3.kommentar,
        reservertFra = reservasjonV3.gyldigFra,
        reservertTil = reservasjonV3.gyldigTil
    )
}