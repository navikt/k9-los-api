package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.db.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import java.time.LocalDateTime
import java.util.*

// Fjernes når V1 skal vekk
class ReservasjonOversetter(
    private val transactionalManager: TransactionalManager,
    private val oppgaveV1Repository: OppgaveRepository,
    private val oppgaveV3Repository: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository,
    private val oppgaveV3RepositoryMedTxWrapper: OppgaveRepositoryTxWrapper,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {


    fun hentReservasjonsnøkkelForOppgavenøkkel(
        oppgaveNøkkel: OppgaveNøkkelDto
    ): String {
        return if (oppgaveNøkkel.erV1Oppgave()) {
            val oppgaveV1 = oppgaveV1Repository.hent(UUID.fromString(oppgaveNøkkel.oppgaveEksternId))
            hentAktivReservasjonFraGammelKontekst(oppgaveV1)!!.reservasjonsnøkkel
        } else {
            oppgaveV3RepositoryMedTxWrapper.hentOppgave(
                oppgaveNøkkel.områdeEksternId,
                oppgaveNøkkel.oppgaveEksternId
            ).reservasjonsnøkkel
        }
    }

    fun hentAktivReservasjonFraGammelKontekst(
        oppgaveV1: Oppgave
    ): ReservasjonV3? {
        return transactionalManager.transaction { tx ->
            val oppgaveV3 =
                oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", oppgaveV1.eksternId.toString())
            reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                oppgaveV3.reservasjonsnøkkel,
                tx
            )
        }
    }

    fun taNyReservasjonFraGammelKontekst(
        oppgaveV1: Oppgave,
        reserverForSaksbehandlerId: Long,
        reservertTil: LocalDateTime,
        utførtAvSaksbehandlerId: Long,
        kommentar: String?
    ): ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            val oppgaveV3 =
                oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", oppgaveV1.eksternId.toString())

            reserverOppgavetypeSomErStøttetIV3(
                oppgaveV3,
                reservertAvSaksbehandlerId = reserverForSaksbehandlerId,
                reservertTil = reservertTil,
                utførtAvSaksbehandlerId = utførtAvSaksbehandlerId,
                kommentar = kommentar,
                tx,
            )
        }
    }

    private fun reserverOppgavetypeSomErStøttetIV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        reservertAvSaksbehandlerId: Long,
        reservertTil: LocalDateTime,
        utførtAvSaksbehandlerId: Long?,
        kommentar: String?,
        tx: TransactionalSession,
    ): ReservasjonV3 {
        check(reservertTil > LocalDateTime.now()) { "Reservert til er i fortiden: $reservertTil" }
        val gyldigFra = LocalDateTime.now()

        return reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktivMenSjekkLegacyFørst(
            reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
            reserverForId = reservertAvSaksbehandlerId,
            gyldigFra = gyldigFra,
            gyldigTil = reservertTil,
            utføresAvId = utførtAvSaksbehandlerId ?: reservertAvSaksbehandlerId,
            kommentar = kommentar ?: "",
            tx = tx
        )
    }
}