package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDatoBakover
import java.time.LocalDateTime
import java.util.*

// Fjernes når V1 skal vekk
class ReservasjonOversetter(
    private val transactionalManager: TransactionalManager,
    private val oppgaveV1Repository: OppgaveRepository,
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgaveV3RepositoryMedTxWrapper: OppgaveRepositoryTxWrapper,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {

    fun hentV1OppgaveFraReservasjon(
        reservasjon: ReservasjonV3
    ): Oppgave? {
        if (reservasjon.reservasjonsnøkkel.startsWith("legacy_")) {
            return oppgaveV1Repository.hent(UUID.fromString(reservasjon.reservasjonsnøkkel.substring(7)))
        } else {
            return null
        }
    }

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
                    val oppgaveV3 =
                        oppgaveV3Tjeneste.hentAktivOppgave(oppgaveV1.eksternId.toString(), "k9sak", "K9", tx)
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        oppgaveV3.reservasjonsnøkkel,
                        tx
                    )
                }

                "K9KLAGE" -> {
                    val oppgaveV3 =
                        oppgaveV3Tjeneste.hentAktivOppgave(oppgaveV1.eksternId.toString(), "k9klage", "K9", tx)
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
                    val oppgaveV3 = oppgaveV3Repository.hentAktivOppgave(
                        oppgaveV1.eksternId.toString(),
                        oppgavetypeRepository.hentOppgavetype("K9", "k9sak"),
                        tx
                    )
                        ?: throw IllegalStateException("ReservasjonV1 for kjent oppgavetype SKAL ha oppgave i OppgaveV3.")

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
                    val oppgaveV3 = oppgaveV3Repository.hentAktivOppgave(
                        oppgaveV1.eksternId.toString(),
                        oppgavetypeRepository.hentOppgavetype("K9", "k9klage"),
                        tx
                    )
                        ?: throw IllegalStateException("ReservasjonV1 for kjent oppgavetype SKAL ha oppgave i OppgaveV3.")

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
        val gyldigFra = if (reservertTil.isAfter(LocalDateTime.now())) {
            LocalDateTime.now().minusHours(24).forskyvReservasjonsDatoBakover()
        } else {
            reservertTil!!.minusHours(24).forskyvReservasjonsDatoBakover()
        }

        if (beslutterErSaksbehandler(oppgave, reserverForSaksbehandlerId)) {
            return null
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
            saksbehandlerRepository.finnSaksbehandlerMedId(brukerIdSomSkalHaReservasjon).brukerIdent

        return oppgave.ansvarligSaksbehandlerIdent == saksbehandlerIdentSomSkalHaReservasjon
    }

    private fun reserverOppgavetypeSomErStøttetIV3(
        oppgave: OppgaveV3,
        reservertAvSaksbehandlerId: Long,
        reservertTil: LocalDateTime,
        utførtAvSaksbehandlerId: Long?,
        kommentar: String?,
        tx: TransactionalSession,
    ): ReservasjonV3 {
        val gyldigFra = if (reservertTil.isAfter(LocalDateTime.now())) {
            LocalDateTime.now().minusHours(24).forskyvReservasjonsDatoBakover()
        } else {
            reservertTil.minusHours(24).forskyvReservasjonsDatoBakover()
        }

        return runBlocking {
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
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
}