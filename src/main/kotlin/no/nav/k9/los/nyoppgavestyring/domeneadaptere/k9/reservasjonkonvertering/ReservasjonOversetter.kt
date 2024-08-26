package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
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
    private val saksbehandlerRepository: SaksbehandlerRepository,
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
            when (oppgaveV1.system) {
                "K9SAK" -> {
                    val oppgaveV3 = oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", oppgaveV1.eksternId.toString())
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        oppgaveV3.reservasjonsnøkkel,
                        tx
                    )
                }

                "K9KLAGE" -> {
                    val oppgaveV3 = oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", oppgaveV1.eksternId.toString())
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        oppgaveV3.reservasjonsnøkkel,
                        tx
                    )
                }

                else -> {
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        "legacy_${oppgaveV1.eksternId}",
                        tx
                    )
                }
            }
        }
    }

    fun taNyReservasjonFraGammelKontekst(
        oppgaveV1: Oppgave,
        reserverForSaksbehandlerId: Long,
        reservertTil: LocalDateTime,
        utførtAvSaksbehandlerId: Long,
        kommentar: String?
    ): ReservasjonV3? {
        return transactionalManager.transaction { tx ->
            when (oppgaveV1.system) {
                "K9SAK" -> {
                    val oppgaveV3 = oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", oppgaveV1.eksternId.toString())

                    reserverOppgavetypeSomErStøttetIV3(
                        oppgaveV3,
                        reservertAvSaksbehandlerId = reserverForSaksbehandlerId,
                        reservertTil = reservertTil,
                        utførtAvSaksbehandlerId = utførtAvSaksbehandlerId,
                        kommentar = kommentar,
                        tx,
                    )
                }

                "K9KLAGE" -> {
                    val oppgaveV3 = oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", oppgaveV1.eksternId.toString())

                    reserverOppgavetypeSomErStøttetIV3(
                        oppgaveV3,
                        reservertAvSaksbehandlerId = reserverForSaksbehandlerId,
                        reservertTil = reservertTil,
                        utførtAvSaksbehandlerId = utførtAvSaksbehandlerId,
                        kommentar = kommentar,
                        tx,
                    )
                }

                else -> {
                    reserverOppgavetypeSomIkkeErStøttetIV3(
                        oppgaveV1,
                        reserverForSaksbehandlerId = reserverForSaksbehandlerId,
                        reservertTil = reservertTil,
                        utførtAvSaksbehandlerId = utførtAvSaksbehandlerId,
                        kommentar = kommentar,
                        tx
                    )
                }
            }
        }
    }

    private fun reserverOppgavetypeSomIkkeErStøttetIV3(
        oppgave: Oppgave,
        reserverForSaksbehandlerId: Long,
        reservertTil: LocalDateTime,
        utførtAvSaksbehandlerId: Long?,
        kommentar: String?,
        tx: TransactionalSession,
    ): ReservasjonV3? {
        check(reservertTil > LocalDateTime.now()) {"Reservert til er i fortiden: $reservertTil"}
        val gyldigFra = LocalDateTime.now()

        if (beslutterErSaksbehandler(oppgave, reserverForSaksbehandlerId)) {
            throw ManglerTilgangException("Saksbehandler kan ikke være beslutter på egen behandling. Saksnummer: ${oppgave.fagsakSaksnummer}")
        }

        return reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
            reservasjonsnøkkel = "legacy_${oppgave.eksternId}",
            reserverForId = reserverForSaksbehandlerId,
            gyldigFra = gyldigFra,
            gyldigTil = reservertTil,
            utføresAvId = utførtAvSaksbehandlerId ?: reserverForSaksbehandlerId,
            kommentar = kommentar ?: "",
            tx = tx
        )
    }

    private fun beslutterErSaksbehandler(
        oppgave: Oppgave,
        brukerIdSomSkalHaReservasjon: Long
    ): Boolean {
        if (!oppgave.tilBeslutter) return false

        val saksbehandlerIdentSomSkalHaReservasjon =
            saksbehandlerRepository.finnSaksbehandlerMedId(brukerIdSomSkalHaReservasjon)?.brukerIdent

        return oppgave.ansvarligSaksbehandlerIdent == saksbehandlerIdentSomSkalHaReservasjon
    }

    private fun reserverOppgavetypeSomErStøttetIV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        reservertAvSaksbehandlerId: Long,
        reservertTil: LocalDateTime,
        utførtAvSaksbehandlerId: Long?,
        kommentar: String?,
        tx: TransactionalSession,
    ): ReservasjonV3 {
        check(reservertTil > LocalDateTime.now()) {"Reservert til er i fortiden: $reservertTil"}
        val gyldigFra = LocalDateTime.now()

        return reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
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