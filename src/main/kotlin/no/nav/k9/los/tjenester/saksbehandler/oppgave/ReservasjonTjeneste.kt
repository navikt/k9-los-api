package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository

class ReservasjonTjeneste constructor(
    private val reservasjonRepository: ReservasjonRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository
){

    internal fun fjernReservasjon(oppgave: Oppgave) {
        if (reservasjonRepository.finnes(oppgave.eksternId)) {
            reservasjonRepository.lagre(oppgave.eksternId) { reservasjon ->
                reservasjon!!.reservertTil = null
                reservasjon
            }
            val reservasjon = reservasjonRepository.hent(oppgave.eksternId)
            saksbehandlerRepository.fjernReservasjonInkluderKode6(
                reservasjon.reservertAv,
                reservasjon.oppgave
            )
        }
    }
}
