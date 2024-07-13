package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.eventhandler.DetaljerMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.HistorikkvaskMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
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
                var t0 = System.nanoTime()
                var eventTeller = 0L
                var behandlingTeller = 0L
                val antallEventIder = behandlingProsessEventK9Repository.hentAntallEventIderUtenVasketHistorikk()
                log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

                while (true) {
                    val behandlingsIder = DetaljerMetrikker.time("k9sakHistorikkvask", "hentBehandlinger") { behandlingProsessEventK9Repository.hentAlleEventIderUtenVasketHistorikk(antall = 1000) }
                    if (behandlingsIder.isEmpty()) {
                        break
                    }

                    if (skalPauses()) {
                        HistorikkvaskMetrikker.observe(TRÅDNAVN, t0)
                        log.info("Vaskejobb satt på pause")
                        Thread.sleep(Duration.ofMinutes(5))
                        t0 = System.nanoTime()
                        continue
                    }

                    log.info("Starter vaskeiterasjon på ${behandlingsIder.size} behandlinger")
                    eventTeller += spillAvBehandlingProsessEventer(behandlingsIder)
                    behandlingTeller += behandlingsIder.count()
                    HistorikkvaskMetrikker.observe(TRÅDNAVN, t0)
                    t0 = System.nanoTime()
                }

                val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
                log.info("Antall oppgaver etter historikkvask (k9-sak): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

                val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
                if (eventTeller > 0) {
                    log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
                }

                log.info("Historikkvask k9sak ferdig")
                nullstillhistorikkvask()
                HistorikkvaskMetrikker.observe(TRÅDNAVN, t0)
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    fun nullstillhistorikkvask(){
        behandlingProsessEventK9Repository.nullstillHistorikkvask()
        log.info("Nullstilt historikkvaskmarkering k9-sak")
    }

    fun skalPauses(): Boolean {
        val zone = ZoneId.of("Europe/Oslo")
        val nå = ZonedDateTime.now().withZoneSameInstant(zone)

        if (nå.toLocalTime() > LocalTime.of(6, 0, 0) && nå.toLocalTime() < LocalTime.of(17, 0, 0) && nå.dayOfWeek <= DayOfWeek.FRIDAY) {
            return true
        }
        return false
    }

    private fun spillAvBehandlingProsessEventer(behandlingsIder: List<UUID>): Long {
        var eventTeller = 0L
        var behandlingTeller = 0L
        val antallBehandlingerIBatch = behandlingsIder.size

        behandlingsIder.forEach { uuid ->
            DetaljerMetrikker.time("k9sakHistorikkvask", "vaskOppgaveForBehandlingKomplett") {
                transactionalManager.transaction { tx ->
                    eventTeller += DetaljerMetrikker.time("k9sakHistorikkvask", "vaskOppgaveForBehandling") { vaskOppgaveForBehandlingUUID(uuid, tx) }
                    behandlingProsessEventK9Repository.markerVasketHistorikk(uuid, tx)
                    behandlingTeller++
                    loggFremgangForHver100(behandlingTeller, "Vasket $behandlingTeller behandlinger av $antallBehandlingerIBatch i gjeldende iterasjon")
                }
            }
        }
        return eventTeller
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID): Long {
        return DetaljerMetrikker.time("k9sakHistorikkvask", "vaskOppgaveForBehandling") {
            transactionalManager.transaction { tx ->
                vaskOppgaveForBehandlingUUID(uuid, tx)
            }
        }
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID, tx: TransactionalSession): Long {
        log.info("Vasker historikk for k9sak-oppgave med eksternId: $uuid")
        var eventTeller = 0L
        var forrigeOppgave: OppgaveV3? = null

        val nyeBehandlingsopplysningerFraK9Sak = DetaljerMetrikker.time("k9sakHistorikkvask", "hentOpplysningerFraK9sak") { k9SakBerikerKlient.hentBehandling(UUID.fromString(uuid.toString()), antallForsøk = 8) }

        val behandlingProsessEventer = DetaljerMetrikker.time("k9sakHistorikkvask", "hentEventer") { behandlingProsessEventK9Repository.hentMedLås(tx, uuid).eventer }
        val høyesteInternVersjon = DetaljerMetrikker.time("k9sakHistorikkvask", "hentHøyesteInternVersjon") {
            oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9sak", "K9", tx)!!
        }
        var eventNrForBehandling = 0L
        var oppgaveDto: OppgaveDto? = null
        for (event in behandlingProsessEventer) {
            if (eventNrForBehandling > høyesteInternVersjon) {
                log.info("Avbryter historikkvask for ${event.eksternId} ved eventTid ${event.eventTid}. Forventer at håndteres av vanlig adaptertjeneste.")
                break //Historikkvasken har funnet eventer som ennå ikke er lastet inn med normalflyt. Dirty eventer skal håndteres av vanlig adaptertjeneste
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

            DetaljerMetrikker.time("k9sakHistorikkvask", "oppdaterEksisterendeOppgaveversjon") { oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, tx) }

            eventTeller++
            loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")

            forrigeOppgave = DetaljerMetrikker.time("k9sakHistorikkvask", "hentOppgaveversjon") {
                oppgaveV3Tjeneste.hentOppgaveversjon(
                    område = "K9", eksternId = oppgaveDto.id, eksternVersjon = oppgaveDto.versjon, tx = tx
                )
            }
            eventNrForBehandling++
        }

        oppgaveDto?.let {
            DetaljerMetrikker.time("k9sakHistorikkvask", "ajourholdAktivOppgave") { oppgaveV3Tjeneste.ajourholdAktivOppgave(oppgaveDto, eventNrForBehandling, tx) }
        }
        log.info("Vasket $eventTeller hendelser for k9sak-oppgave med eksternId: $uuid")
        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

}
