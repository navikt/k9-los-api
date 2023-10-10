package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
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
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDatoBakover
import java.time.LocalDateTime
import java.util.*

class ReservasjonOversetter(
    private val transactionalManager: TransactionalManager,
    private val oppgaveV1Repository: OppgaveRepository,
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {

    fun hentV1OppgaveFraReservasjon(
        reservasjon: ReservasjonV3
    ) : Oppgave? {
        if (reservasjon.reservasjonsnøkkel.startsWith("legacy_")) {
            return oppgaveV1Repository.hent(UUID.fromString(reservasjon.reservasjonsnøkkel.substring(7)))
        } else {
            return null
        }
    }

    fun hentNyReservasjonFraGammelKontekst(
        oppgaveV1: Oppgave
    )
        : ReservasjonV3 {
        return transactionalManager.transaction { tx ->
            when (oppgaveV1.system) {
                "K9SAK" -> {
                    val oppgaveV3 =
                        oppgaveV3Tjeneste.hentAktivOppgave(oppgaveV1.eksternId.toString(), "k9sak", "K9", tx)
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        oppgaveV3.reservasjonsnøkkel,
                        tx
                    )!!
                }

                "K9KLAGE" -> {
                    val oppgaveV3 =
                        oppgaveV3Tjeneste.hentAktivOppgave(oppgaveV1.eksternId.toString(), "k9klage", "K9", tx)
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        oppgaveV3.reservasjonsnøkkel,
                        tx
                    )!!
                }

                else -> {
                    reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                        "legacy_${oppgaveV1.eksternId}",
                        tx
                    )!!
                }
            }
        }
    }

    fun taNyReservasjonFraGammelKontekst(
        oppgaveV1: Oppgave,
        reservertAvEpost: String,
        reservertTil: LocalDateTime,
        utførtAvIdent: String?,
        kommentar: String?
    )
        : ReservasjonV3 {
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
                        reservertAvEpost = reservertAvEpost,
                        reservertTil = reservertTil,
                        utførtAvIdent = utførtAvIdent,
                        kommentar = kommentar,
                    )
                }

                "K9KLAGE" -> {
                    val oppgaveV3 = oppgaveV3Repository.hentAktivOppgave(
                        oppgaveV1.eksternId.toString(),
                        oppgavetypeRepository.hentOppgavetype("K9", "k9sak"),
                        tx
                    )
                        ?: throw IllegalStateException("ReservasjonV1 for kjent oppgavetype SKAL ha oppgave i OppgaveV3.")

                    reserverOppgavetypeSomErStøttetIV3(
                        oppgaveV3,
                        reservertAvEpost = reservertAvEpost,
                        reservertTil = reservertTil,
                        utførtAvIdent = utførtAvIdent,
                        kommentar = kommentar,
                    )
                }

                else -> {
                    reserverOppgavetypeSomIkkeErStøttetIV3(
                        oppgaveV1.eksternId.toString(),
                        reservertAvEpost = reservertAvEpost,
                        reservertTil = reservertTil,
                        utførtAvIdent = utførtAvIdent,
                        kommentar = kommentar,
                    )
                }
            }
        }
    }

    private fun reserverOppgavetypeSomIkkeErStøttetIV3(
        oppgaveEksternId: String,
        reservertAvEpost: String,
        reservertTil: LocalDateTime,
        utførtAvIdent: String?,
        kommentar: String?
    ): ReservasjonV3 {
        val gyldigFra = if (reservertTil.isAfter(LocalDateTime.now())) {
            LocalDateTime.now().minusHours(24).forskyvReservasjonsDatoBakover()
        } else {
            reservertTil!!.minusHours(24).forskyvReservasjonsDatoBakover()
        }

        val reservertAv = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(reservertAvEpost)!!
        }

        val utførtAv = runBlocking {
            utførtAvIdent?.let { utførtAvIdent ->
                saksbehandlerRepository.finnSaksbehandlerMedIdent(utførtAvIdent)!!
            }
        }

        return reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
            reservasjonsnøkkel = "legacy_$oppgaveEksternId",
            reserverForId = reservertAv.id!!,
            gyldigFra = gyldigFra,
            gyldigTil = reservertTil,
            utføresAvId = utførtAv?.let { it.id!! } ?: reservertAv.id!!,
            kommentar = kommentar ?: ""
        )
    }

    private fun reserverOppgavetypeSomErStøttetIV3(
        oppgave: OppgaveV3,
        reservertAvEpost: String,
        reservertTil: LocalDateTime,
        utførtAvIdent: String?,
        kommentar: String?
    ): ReservasjonV3 {
        val reservertAv = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedEpost(
                reservertAvEpost
            )
        }!!

        val gyldigFra = if (reservertTil.isAfter(LocalDateTime.now())) {
            LocalDateTime.now().minusHours(24).forskyvReservasjonsDatoBakover()
        } else {
            reservertTil.minusHours(24).forskyvReservasjonsDatoBakover()
        }

        val flyttetAv = runBlocking {
            utførtAvIdent?.let { flyttetAv ->
                saksbehandlerRepository.finnSaksbehandlerMedIdent(flyttetAv)!!
            }
        }

        return runBlocking {
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                reserverForId = reservertAv.id!!,
                gyldigFra = gyldigFra,
                gyldigTil = reservertTil,
                utføresAvId = flyttetAv?.let { it.id!! } ?: reservertAv.id!!,
                kommentar = kommentar ?: ""
            )
        }
    }
}