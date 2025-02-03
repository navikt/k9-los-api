package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.tilbaketillos

import kotlinx.coroutines.*
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.los.eventhandler.DetaljerMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.HistorikkvaskMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.TilbakeEventTilDtoMapper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class K9TilbakeTilLosHistorikkvaskTjeneste(
    private val behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
) {

    private val log: Logger = LoggerFactory.getLogger(K9TilbakeTilLosHistorikkvaskTjeneste::class.java)

    private val METRIKKLABEL = "k9-tilbake-til-los-historikkvask"

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9tilbake-hendelser")
            val start = System.currentTimeMillis()

            val dispatcher = newFixedThreadPoolContext(2, "Historikkvask k9tilbake")

            log.info("Starter avspilling av historiske BehandlingProsessEventer")

            val tidKjøringStartet = System.currentTimeMillis()
            var t0 = System.nanoTime()
            var eventTeller = 0L
            var behandlingTeller = 0L
            val antallEventIder = behandlingProsessEventTilbakeRepository.hentAntallEventIderUtenVasketHistorikk()
            log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

            while (true) {
                val behandlingsIder = DetaljerMetrikker.time(
                    "k9tilbakeHistorikkvask",
                    "hentBehandlinger"
                ) { behandlingProsessEventTilbakeRepository.hentAlleEventIderUtenVasketHistorikk(antall = 2000) }
                if (behandlingsIder.isEmpty()) {
                    break
                }

                log.info("Starter vaskeiterasjon på ${behandlingsIder.size} behandlinger")
                eventTeller += spillAvBehandlingProsessEventer(dispatcher, behandlingsIder)
                behandlingTeller += behandlingsIder.count()
                log.info("Vasket iterasjon med ${behandlingsIder.size} behandlinger, har vasket totalt $behandlingTeller av $antallEventIder")
                HistorikkvaskMetrikker.observe(METRIKKLABEL, t0)
                t0 = System.nanoTime()
            }

            val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
            log.info("Antall oppgaver etter historikkvask k9-tilbake: $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

            val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
            if (eventTeller > 0) {
                log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
            }

            log.info("Historikkvask k9tilbake ferdig, tid brukt: {} ms", (System.currentTimeMillis() - start))
            nullstillhistorikkvask()
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun nullstillhistorikkvask() {
        behandlingProsessEventTilbakeRepository.nullstillHistorikkvask()
        log.info("Nullstilt historikkvaskmarkering k9-tilbake")
    }

    private fun spillAvBehandlingProsessEventer(
        dispatcher: ExecutorCoroutineDispatcher,
        behandlingsIder: List<UUID>
    ): Long {
        val scope = CoroutineScope(dispatcher)

        val jobber = behandlingsIder.map {
            scope.async { runBlocking { vaskOppgaveForBehandlingUUIDOgMarkerVasket(it) } }
        }.toList()

        val eventTeller = runBlocking {
            jobber.sumOf { it.await() }
        }
        return eventTeller
    }

    fun vaskOppgaveForBehandlingUUIDOgMarkerVasket(uuid: UUID): Long {
        var eventTeller = 0L
        DetaljerMetrikker.time("k9tilbakeHistorikkvask", "vaskOppgaveForBehandlingKomplett") {
            transactionalManager.transaction { tx ->
                eventTeller = DetaljerMetrikker.time(
                    "k9tilbakeHistorikkvask",
                    "vaskOppgaveForBehandling"
                ) { vaskOppgaveForBehandlingUUID(uuid, tx) }
                behandlingProsessEventTilbakeRepository.markerVasketHistorikk(uuid, tx)
            }
        }
        return eventTeller
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID): Long {
        return DetaljerMetrikker.time("k9tilbakeHistorikkvask", "vaskOppgaveForBehandling") {
            transactionalManager.transaction { tx ->
                vaskOppgaveForBehandlingUUID(uuid, tx)
            }
        }
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID, tx: TransactionalSession): Long {
        log.info("Vasker historikk for k9tilbake-oppgave med eksternId: $uuid")
        var forrigeOppgave: OppgaveV3? = null

        val behandlingProsessEventer = DetaljerMetrikker.time(
            "k9tilbakeHistorikkvask",
            "hentEventer"
        ) { behandlingProsessEventTilbakeRepository.hentMedLås(tx, uuid).eventer }
        val høyesteInternVersjon = DetaljerMetrikker.time("k9tilbakeHistorikkvask", "hentHøyesteInternVersjon") {
            oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9tilbake", "K9", tx)
        }
        var eventNrForBehandling = 0L
        var oppgaveV3: OppgaveV3? = null
        for (event in behandlingProsessEventer) {
            if (høyesteInternVersjon != null && eventNrForBehandling > høyesteInternVersjon) {
                log.info("Avbryter historikkvask for ${event.eksternId} ved eventTid ${event.eventTid}. Forventer at håndteres av vanlig adaptertjeneste.")
                break //Historikkvasken har funnet eventer som ennå ikke er lastet inn med normalflyt. Dirty eventer skal håndteres av vanlig adaptertjeneste
            }
            val oppgaveDto = TilbakeEventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)

            oppgaveV3 = DetaljerMetrikker.time(
                "k9tilbakeHistorikkvask",
                "utledEksisterendeOppgaveversjon"
            ) { oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, tx) }
            DetaljerMetrikker.time(
                "k9tilbakeHistorikkvask",
                "oppdaterEksisterendeOppgaveversjon"
            ) { oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveV3, eventNrForBehandling, tx) }

            forrigeOppgave = DetaljerMetrikker.time("k9tilbakeHistorikkvask", "hentOppgaveversjon") {
                oppgaveV3Tjeneste.hentOppgaveversjon(
                    område = "K9", eksternId = oppgaveDto.id, eksternVersjon = oppgaveDto.versjon, tx = tx
                )
            }
            eventNrForBehandling++
        }

        oppgaveV3?.let {
            val ytelsetypefraOppgaven =
                oppgaveV3.felter.filter { it.oppgavefelt.feltDefinisjon.eksternId == "ytelsestype" }.map { it.verdi }
                    .firstOrNull()
            if (ytelsetypefraOppgaven == no.nav.k9.kodeverk.behandling.FagsakYtelseType.FRISINN.kode) {
                DetaljerMetrikker.time(
                    "k9tilbakeHistorikkvask",
                    "slettAktivOppgave"
                ) { oppgaveV3Tjeneste.slettAktivOppgave(oppgaveV3, tx) }
                log.info("oppgave ${oppgaveV3.eksternId} gjelder FRISINN, fjerner oppgaven fra aktiv-tabellene")
            } else {
                DetaljerMetrikker.time(
                    "k9tilbakeHistorikkvask",
                    "ajourholdAktivOppgave"
                ) { oppgaveV3Tjeneste.ajourholdAktivOppgave(oppgaveV3, eventNrForBehandling, tx) }
            }
        }
        log.info("Vasket $eventNrForBehandling hendelser for k9tilbake-oppgave med eksternId: $uuid")
        return eventNrForBehandling
    }
}
