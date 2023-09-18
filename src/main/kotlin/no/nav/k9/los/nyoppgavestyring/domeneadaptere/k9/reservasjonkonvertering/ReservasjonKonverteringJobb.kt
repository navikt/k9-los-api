package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDatoBakover
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.concurrent.thread

class ReservasjonKonverteringJobb(
    private val config: Configuration,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val transactionalManager: TransactionalManager,
) {

    private val log: Logger = LoggerFactory.getLogger(ReservasjonKonverteringJobb::class.java)
    private val TRÅDNAVN = "reservasjonKonvertering"

    fun kjør() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Spiller av reservasjoner i gammel løsning og skriver til ny modell")
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN,
            ) {
                spillAvReservasjoner()
            }
        }
    }

    private fun spillAvReservasjoner() {

        log.info("Stareter avspilling av reservasjoner")
        val tidKjøringStartet = System.currentTimeMillis()

        val reservasjonIder = reservasjonRepository.hentAlleReservasjonUUID()
        log.info("Fant ${reservasjonIder.size} behandlinger")

        reservasjonIder.forEach { gammelReservasjonUuid ->
            val reservasjonV1 = reservasjonRepository.hent(gammelReservasjonUuid)
            //TODO filtrer bort gamle og/eller ugyldige reservasjoner?
            if (reservasjonV1.reservertTil == null) {
                return //Logisk slettet reservasjon. Migreres ikke
            }
            val oppgaveV1 = oppgaveRepository.hent(reservasjonV1.oppgave)
            transactionalManager.transaction { tx ->
                val gammelReservasjon = reservasjonRepository.hentSisteReservasjonMedLås(gammelReservasjonUuid, tx)
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
                            gammelReservasjon
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
                            gammelReservasjon
                        )
                    }

                    else -> {
                        reserverOppgavetypeSomIkkeErStøttetIV3(oppgaveV1.eksternId.toString(), gammelReservasjon)
                    }
                }
            }
        }
    }

    private fun reserverOppgavetypeSomIkkeErStøttetIV3(
        oppgaveEksternId: String,
        gammelReservasjon: Reservasjon
    ) {
        val gyldigFra = if (gammelReservasjon.reservertTil!!.isAfter(LocalDateTime.now())) {
            LocalDateTime.now().minusHours(24).forskyvReservasjonsDatoBakover()
        } else {
            gammelReservasjon.reservertTil!!.minusHours(24).forskyvReservasjonsDatoBakover()
        }

        val reservertAv = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(
                gammelReservasjon.reservertAv
            )!!
        }

        val flyttetAv = runBlocking {
            gammelReservasjon.flyttetAv?.let {flyttetAv ->
                saksbehandlerRepository.finnSaksbehandlerMedIdent(flyttetAv)!!
            }
        }

        reservasjonV3Tjeneste.taReservasjon(
            reservasjonsnøkkel = "legacy_$oppgaveEksternId",
            reserverForId = reservertAv.id!!,
            gyldigFra = gyldigFra,
            gyldigTil = gammelReservasjon.reservertTil!!,
            utføresAvId = flyttetAv?.let { it.id!! } ?: reservertAv.id!!,
        )
    }

    private fun reserverOppgavetypeSomErStøttetIV3(
        oppgave: OppgaveV3,
        gammelReservasjon: Reservasjon
    ) {
        val reservertAv = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedEpost(
                gammelReservasjon.flyttetAv ?: gammelReservasjon.reservertAv
            )
        }!!

        val gyldigFra = if (gammelReservasjon.reservertTil!!.isAfter(LocalDateTime.now())) {
            LocalDateTime.now().minusHours(24).forskyvReservasjonsDatoBakover()
        } else {
            gammelReservasjon.reservertTil!!.minusHours(24).forskyvReservasjonsDatoBakover()
        }

        val flyttetAv = runBlocking {
            gammelReservasjon.flyttetAv?.let {flyttetAv ->
                saksbehandlerRepository.finnSaksbehandlerMedIdent(flyttetAv)!!
            }
        }

        runBlocking {
            reservasjonV3Tjeneste.taReservasjon(
                reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                reserverForId = reservertAv.id!!,
                gyldigFra = gyldigFra,
                gyldigTil = gammelReservasjon.reservertTil!!,
                utføresAvId = flyttetAv?.let { it.id!! } ?: reservertAv.id!!,
            )
        }
    }
}