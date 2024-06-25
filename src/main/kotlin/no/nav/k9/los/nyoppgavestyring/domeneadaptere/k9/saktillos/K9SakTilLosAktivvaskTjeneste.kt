package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.AktivvaskMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class K9SakTilLosAktivvaskTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAktivvaskTjeneste::class.java)
    private val TRÅDNAVN = "k9-sak-til-los-aktivvask"

    fun kjørAktivvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av aktive oppgaver mot historiske k9sak-hendelser")
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN,
            ) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10))
                log.info("Starter avspilling av historiske BehandlingProsessEventer")

                val tidKjøringStartet = System.currentTimeMillis()
                var t0 = System.nanoTime()
                var eventTeller = 0L
                var behandlingTeller = 0L
                val antallEventIder = behandlingProsessEventK9Repository.hentAntallEventIderUtenVasketAktiv()
                log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

                while (true) {
                    val behandlingsIder =
                        behandlingProsessEventK9Repository.hentAlleEventIderUtenVasketAktivOgIkkeDirty(antall = 1000)
                    if (behandlingsIder.isEmpty()) {
                        break
                    }

                    if (skalPauses()) {
                        AktivvaskMetrikker.observe(TRÅDNAVN, t0)
                        log.info("Vaskejobb satt på pause")
                        Thread.sleep(Duration.ofMinutes(5))
                        t0 = System.nanoTime()
                        continue
                    }

                    log.info("Starter vaskeiterasjon på ${behandlingsIder.size} behandlinger")
                    eventTeller += spillAvBehandlingProsessEventer(behandlingsIder)
                    behandlingTeller += behandlingsIder.count()
                    AktivvaskMetrikker.observe(TRÅDNAVN, t0)
                    t0 = System.nanoTime()
                }

                val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
                log.info("Antall oppgaver etter aktivvask (k9-sak): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

                val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
                if (eventTeller > 0) {
                    log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
                }
                AktivvaskMetrikker.observe(TRÅDNAVN, t0)
                log.info("Aktivvask k9sak ferdig")
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    fun skalPauses(): Boolean {
        return false
        /*
        if (KoinProfile.PREPROD == config.koinProfile()) {
            return false
        }
        val nå = LocalTime.now()
        if (nå > LocalTime.of(6, 0, 0) && nå < LocalTime.of(
                17,
                0,
                0
            ) && LocalDateTime.now().dayOfWeek <= DayOfWeek.FRIDAY
        ) {
            return true
        }
        return false

         */
    }

    private fun spillAvBehandlingProsessEventer(behandlingsIder: List<UUID>): Long {
        var oppgaveteller = 0L
        var behandlingTeller = 0L
        val antallBehandlingerIBatch = behandlingsIder.size

        behandlingsIder.forEach { uuid ->
            transactionalManager.transaction { tx ->
                oppgaveteller += vaskOppgaveForBehandlingUUID(uuid, tx)
            }
            behandlingTeller++
            loggFremgangForHver100(
                behandlingTeller,
                "Vasket $behandlingTeller behandlinger av $antallBehandlingerIBatch i gjeldende iterasjon"
            )
        }
        return oppgaveteller
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID, tx: TransactionalSession): Long {
        val behandlingProsessEventer = behandlingProsessEventK9Repository.hentMedLås(tx, uuid).eventer

        val høyesteInternVersjon =
            oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9sak", "K9", tx)!!

        var eventNrForBehandling = behandlingProsessEventer.size-1
        if (eventNrForBehandling > høyesteInternVersjon) {
            log.warn("Skipper aktivvask av behandling: $uuid")
            return 0 //ligge unna oppgaver som ikke er oppdatert. De blir straks oppdatert av normalflyt
        }

        val event = behandlingProsessEventer.last()
        val forrigeOppgave = if (eventNrForBehandling > 0) { oppgaveV3Tjeneste.hentOppgaveVersjonenFør(uuid.toString(), høyesteInternVersjon, "k9sak", "K9", tx) } else null

        val nyeBehandlingsopplysningerFraK9Sak = k9SakBerikerKlient.hentBehandling(UUID.fromString(uuid.toString()))
        if (event.eldsteDatoMedEndringFraSøker == null && nyeBehandlingsopplysningerFraK9Sak != null && nyeBehandlingsopplysningerFraK9Sak.eldsteDatoMedEndringFraSøker != null) {
            event.copy(eldsteDatoMedEndringFraSøker = nyeBehandlingsopplysningerFraK9Sak.eldsteDatoMedEndringFraSøker)
            //ser ut som noen gamle mottatte dokumenter kan mangle innsendingstidspunkt.
            //da faller vi tilbake til å bruke behandling_opprettet i mapperen
        }

        var oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)

        oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
            event,
            oppgaveDto,
            nyeBehandlingsopplysningerFraK9Sak
        )

        oppgaveV3Tjeneste.ajourholdAktivOppgave(oppgaveDto, eventNrForBehandling.toLong(), tx)
        log.info("Ajourholdt aktiv oppgave for behandling: $uuid")
        return 1
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

}
