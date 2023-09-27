package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import java.time.LocalDateTime

data class ReservasjonV3Dto ( //TODO: WIP avklare med Vebjørn hvor mange felter som trengs jfr OppgaveDto som returneres fra gammel fa-oppgave-fra-ko
    val reservasjonsnøkkel: String,
    val reservertAv: String,
    val reservertFra: LocalDateTime,
    val reservertTil: LocalDateTime?,
) {
    constructor(reservasjonV3: ReservasjonV3, reservertAv: Saksbehandler) : this (
        reservasjonsnøkkel = reservasjonV3.reservasjonsnøkkel,
        reservertAv = reservertAv.epost,
        reservertFra = reservasjonV3.gyldigFra,
        reservertTil = reservasjonV3.gyldigTil
    )
}
