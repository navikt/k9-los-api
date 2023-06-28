package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.mottak.reservasjon.ReservasjonV3Repository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.concurrent.thread

class ReservasjonKonverteringJobb(
    private val config: Configuration,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Repository: ReservasjonV3Repository,
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
        /*
        log.info("Stareter avspilling av reservasjoner")
        val tidKjøringStartet = System.currentTimeMillis()

        val reservasjonIder = reservasjonRepository.hentAlleBehandlingUUID()
        log.info("Fant ${reservasjonIder.size} behandlinger")

        reservasjonIder.forEach { uuid ->
            val oppgaveV1 = oppgaveRepository.hent(uuid)
            val oppgavetype = when (oppgaveV1.system) {
                "K9SAK" -> "k9sak"
                "K9KLAGE" -> "k9klage"
                else -> return@forEach
            }
            transactionalManager.transaction { tx ->
                val oppgaveV3 = oppgaveV3Repository.hentAktivOppgave(
                    uuid.toString(),
                    oppgavetypeRepository.hentOppgavetype("K9", oppgavetype),
                    tx
                )
                val reservasjonForBehandling = reservasjonRepository.hentSisteReservasjonMedLås(uuid)
                if (reservasjonForBehandling.reservertTil != null) {
                    val nyReservasjon = ReservasjonV3(
                        saksbehandlerEpost = runBlocking {
                            saksbehandlerRepository.finnSaksbehandlerMedIdent(
                                reservasjonForBehandling.reservertAv
                            )!!.epost
                        },
                        reservasjonsnøkkel = oppgaveV3!!.reservasjonsnøkkel,
                        gyldigFra = LocalDateTime.MIN,
                        gyldigTil = reservasjonForBehandling.reservertTil!!,
                    )
                    reservasjonV3Repository.lagreReservasjon(nyReservasjon)
                }
            }
        }

         */
    }
}