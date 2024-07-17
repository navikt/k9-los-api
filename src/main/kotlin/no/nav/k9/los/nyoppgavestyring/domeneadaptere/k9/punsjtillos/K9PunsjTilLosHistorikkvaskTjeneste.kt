package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos

import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.HistorikkvaskMetrikker
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*
import kotlin.concurrent.thread

class K9PunsjTilLosHistorikkvaskTjeneste(
    private val eventRepository: PunsjEventK9Repository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager
) {
    private val log: Logger = LoggerFactory.getLogger(K9PunsjTilLosHistorikkvaskTjeneste::class.java)
    private val TRÅDNAVN = "k9-punsj-til-los-historikkvask"

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9punsj-hendelser")
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
                val antallEventIder = eventRepository.hentAntallEventIderUtenVasketHistorikk()
                log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

                while (true) {
                    val behandlingsIder = eventRepository.hentAlleEventIderUtenVasketHistorikk(antall = 1000)
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
                log.info("Antall oppgaver etter historikkvask (k9-punsj): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

                val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
                if (eventTeller > 0) {
                    log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
                }

                log.info("Historikkvask k9punsj ferdig")
                eventRepository.nullstillHistorikkvask()
                log.info("Nullstilt historikkvaskmarkering k9-punsj")
                HistorikkvaskMetrikker.observe(TRÅDNAVN, t0)
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvBehandlingProsessEventer(behandlingsIder: List<UUID>): Long {
        var eventTeller = 0L
        var behandlingTeller = 0L
        val antallBehandlingerIBatch = behandlingsIder.size

        behandlingsIder.forEach { uuid ->
            transactionalManager.transaction { tx ->
                eventTeller += vaskOppgaveForBehandlingUUID(uuid, tx)
                eventRepository.markerVasketHistorikk(uuid, tx)
                behandlingTeller++
                loggFremgangForHver100(
                    behandlingTeller,
                    "Vasket $behandlingTeller behandlinger av $antallBehandlingerIBatch i gjeldende iterasjon"
                )
            }
        }
        return eventTeller
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID): Long {
        return transactionalManager.transaction { tx ->
            vaskOppgaveForBehandlingUUID(uuid, tx)
        }
    }

    fun skalPauses(): Boolean {
        val zone = ZoneId.of("Europe/Oslo")
        val nå = ZonedDateTime.now().withZoneSameInstant(zone)

        if (config.koinProfile == KoinProfile.PROD && nå.toLocalTime() > LocalTime.of(6, 0, 0) && nå.toLocalTime() < LocalTime.of(17, 0, 0) && nå.dayOfWeek <= DayOfWeek.FRIDAY) {
            return true
        }
        return false
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID, tx: TransactionalSession): Long {
        log.info("Vasker historikk for k9punsj-oppgave med eksternId: $uuid")
        var eventTeller = 0L
        var forrigeOppgave: OppgaveV3? = null

        var eventNrForBehandling = 0L
        val behandlingProsessEventer: List<PunsjEventDto> = eventRepository.hentMedLås(tx, uuid).eventer
        var oppgaveV3: OppgaveV3? = null
        for (event in behandlingProsessEventer) {
            var oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)

            oppgaveV3 = oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, tx)
            oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveV3, eventNrForBehandling, tx)

            forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                område = "k9",
                eksternId = oppgaveDto.id,
                eksternVersjon = oppgaveDto.versjon,
                tx = tx
            )
        }

        oppgaveV3?.let {
            oppgaveV3Tjeneste.ajourholdAktivOppgave(it, eventNrForBehandling, tx)
        }

        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

}
