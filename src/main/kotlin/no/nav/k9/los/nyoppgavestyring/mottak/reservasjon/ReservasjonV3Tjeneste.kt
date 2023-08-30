package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import java.time.LocalDateTime

class ReservasjonV3Tjeneste(
    private val transactionalManager: TransactionalManager,
    private val reservasjonV3Repository: ReservasjonV3Repository,
) {
    fun taReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        utføresAvId: Long
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            sjekkOgHåndterEksisterendeReservasjon(
                reservasjonsnøkkel,
                reserverForId,
                gyldigFra,
                gyldigTil,
                utføresAvId,
                tx
            )
        }
    }

    private fun sjekkOgHåndterEksisterendeReservasjon(
        reservasjonsnøkkel: String,
        reserverForId: Long,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        utføresAvId: Long,
        tx: TransactionalSession
    ): ReservasjonV3 {
        val aktivReservasjon =
            reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(
                reservasjonsnøkkel,
                tx
            )

        if (aktivReservasjon == null) {
            val reservasjonTilLagring = ReservasjonV3(
                reservertAv = reserverForId,
                reservasjonsnøkkel = reservasjonsnøkkel,
                gyldigFra = gyldigFra,
                gyldigTil = gyldigTil,
            )
            return reservasjonV3Repository.lagreReservasjon(reservasjonTilLagring, tx)
        }

        if (reserverForId != aktivReservasjon.reservertAv) { // reservert av andre
            return aktivReservasjon
        }

        if (aktivReservasjon.gyldigTil < gyldigTil) {
            return reservasjonV3Repository.forlengReservasjon(
                aktivReservasjon,
                endretAv = utføresAvId,
                nyTildato = gyldigTil,
                tx
            )
        }

        //allerede reservert lengre enn ønsket
        // TODO: kort ned reservasjon i stedet? Avklaring neste uke. Sjekke opp mot V1-logikken
        // TODO: Alt 1. - kort ned reservasjon dersom det er innlogget bruker sin reservasjon som endres. Ellers IllegalArgument.
        // TODO: Alt 2. - Alltid feilmelding eller "ikke utført", for så å tvinge kall mot "endre reservasjon()" eller lignende
        return aktivReservasjon
    }

    fun hentReservasjonerForSaksbehandler(saksbehandlerId: Long): List<ReservasjonV3> {
        return transactionalManager.transaction { tx ->
            reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandlerId, tx)
        }
    }


    fun annullerReservasjon(reservasjonsnøkkel: String, annullertAvBrukerId: Long) {
        transactionalManager.transaction { tx ->
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)!!
            reservasjonV3Repository.annullerAktivReservasjonOgLagreEndring(
                aktivReservasjon,
                annullertAvBrukerId,
                tx
            )
        }
    }

    fun overførReservasjon(reservasjonsnøkkel: String, reserverTil: LocalDateTime, tilSaksbehandlerId: Long, utførtAvBrukerId: Long) {
        transactionalManager.transaction { tx ->
            val aktivReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel(reservasjonsnøkkel, tx)!!
            reservasjonV3Repository.overførReservasjon(
                aktivReservasjon = aktivReservasjon,
                saksbehandlerSomSkalHaReservasjonId = tilSaksbehandlerId,
                endretAvBrukerId = utførtAvBrukerId,
                reserverTil = reserverTil,
                tx
            )
        }
    }
}