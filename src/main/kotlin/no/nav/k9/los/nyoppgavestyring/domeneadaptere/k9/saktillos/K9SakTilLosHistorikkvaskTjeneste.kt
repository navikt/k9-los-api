package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.concurrent.thread

class K9SakTilLosHistorikkvaskTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosHistorikkvaskTjeneste::class.java)
    private val TRÅDNAVN = "k9-sak-til-los-historikkvask"

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9sak-hendelser")
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN
            ) {
                log.info("Starter avspilling av historiske BehandlingProsessEventer")

                val tidKjøringStartet = System.currentTimeMillis()
                var eventTeller = 0L
                var behandlingTeller = 0L
                val antallEventIder = behandlingProsessEventK9Repository.hentAntallEventIderUtenVasketHistorikk()
                log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

                while (true) {
                    val behandlingsIder = behandlingProsessEventK9Repository.hentAlleEventIderUtenVasketHistorikk(antall = 1000)
                    if (behandlingsIder.isEmpty()) {
                        break
                    }

                    if (skalPauses()) {
                        log.info("Vaskejobb satt på pause")
                        Thread.sleep(Duration.ofMinutes(5))
                        continue
                    }

                    log.info("Starter vaskeiterasjon på ${behandlingsIder.size} behandlinger")
                    eventTeller += spillAvBehandlingProsessEventer(behandlingsIder)
                    behandlingTeller += behandlingsIder.count()
                }

                val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
                log.info("Antall oppgaver etter historikkvask (k9-sak): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

                val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
                if (eventTeller > 0) {
                    log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
                }

                log.info("Historikkvask k9sak ferdig")
                behandlingProsessEventK9Repository.nullstillHistorikkvask()
                log.info("Nullstilt historikkvaskmarkering k9-sak")
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    fun skalPauses(): Boolean {
        val nå = LocalTime.now()
        if (nå > LocalTime.of(6, 0, 0) && nå < LocalTime.of(17, 0, 0) && LocalDateTime.now().dayOfWeek <= DayOfWeek.FRIDAY) {
            return true
        }
        return false
    }

    private fun spillAvBehandlingProsessEventer(behandlingsIder: List<UUID>): Long {
        var eventTeller = 0L
        var behandlingTeller = 0L
        val antallBehandlingerIBatch = behandlingsIder.size

        behandlingsIder.forEach { uuid ->
            transactionalManager.transaction { tx ->
                eventTeller += vaskOppgaveForBehandlingUUID(uuid, tx)
                behandlingProsessEventK9Repository.markerVasketHistorikk(uuid, tx)
                behandlingTeller++
                loggFremgangForHver100(behandlingTeller, "Vasket $behandlingTeller behandlinger av $antallBehandlingerIBatch i gjeldende iterasjon")
            }
        }
        return eventTeller
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID): Long {
        return transactionalManager.transaction { tx ->
            vaskOppgaveForBehandlingUUID(uuid, tx)
        }
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID, tx: TransactionalSession): Long {
        log.info("Vasker historikk for k9sak-oppgave med eksternId: $uuid")
        var eventTeller = 0L
        var forrigeOppgave: OppgaveV3? = null

        val nyeBehandlingsopplysningerFraK9Sak = k9SakBerikerKlient.hentBehandling(UUID.fromString(uuid.toString()))

        val behandlingProsessEventer = behandlingProsessEventK9Repository.hentMedLås(tx, uuid).eventer
        val høyesteInternVersjon =
            oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9sak", "K9", tx)!!
        var eventNrForBehandling = 0L
        var oppgaveDto: OppgaveDto? = null
        for (event in behandlingProsessEventer) {
            if (eventNrForBehandling > høyesteInternVersjon) {
                break
            }
            if (event.eldsteDatoMedEndringFraSøker == null && nyeBehandlingsopplysningerFraK9Sak != null && nyeBehandlingsopplysningerFraK9Sak.eldsteDatoMedEndringFraSøker != null) {
                event.copy(eldsteDatoMedEndringFraSøker = nyeBehandlingsopplysningerFraK9Sak.eldsteDatoMedEndringFraSøker)
                //ser ut som noen gamle mottatte dokumenter kan mangle innsendingstidspunkt.
                //da faller vi tilbake til å bruke behandling_opprettet i mapperen
            }
            oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)

            oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
                event,
                oppgaveDto,
                nyeBehandlingsopplysningerFraK9Sak
            )

            oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, høyesteInternVersjon, tx)

            eventTeller++
            loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")

            forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                område = "K9", eksternId = oppgaveDto.id, eksternVersjon = oppgaveDto.versjon, tx = tx
            )
            eventNrForBehandling++
        }

        oppgaveDto?.let {
            oppgaveV3Tjeneste.ajourholdAktivOppgave(oppgaveDto, eventNrForBehandling, tx)
        }

        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

}
