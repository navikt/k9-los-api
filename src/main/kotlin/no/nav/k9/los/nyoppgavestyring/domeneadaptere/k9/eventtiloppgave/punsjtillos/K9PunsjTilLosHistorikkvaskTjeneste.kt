package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.HistorikkvaskMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class K9PunsjTilLosHistorikkvaskTjeneste(
    private val k9PunsjEventRepository: K9PunsjEventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager
) {
    private val log: Logger = LoggerFactory.getLogger(K9PunsjTilLosHistorikkvaskTjeneste::class.java)
    val METRIKKLABEL = "k9-punsj-til-los-historikkvask"

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9punsj-hendelser")

            val tidKjøringStartet = System.currentTimeMillis()
            var t0 = System.nanoTime()
            var eventTeller = 0L
            var behandlingTeller = 0L
            val antallEventIder = k9PunsjEventRepository.hentAntallEventIderUtenVasketHistorikk()
            log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

            while (true) {
                val behandlingsIder = k9PunsjEventRepository.hentAlleEventIderUtenVasketHistorikk(antall = 1000)
                if (behandlingsIder.isEmpty()) {
                    break
                }

                log.info("Starter vaskeiterasjon på ${behandlingsIder.size} behandlinger")
                eventTeller += spillAvBehandlingProsessEventer(behandlingsIder)
                behandlingTeller += behandlingsIder.count()
                HistorikkvaskMetrikker.observe(METRIKKLABEL, t0)
                t0 = System.nanoTime()
            }

            val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
            log.info("Antall oppgaver etter historikkvask (k9-punsj): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

            val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
            if (eventTeller > 0) {
                log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
            }

            log.info("Historikkvask k9punsj ferdig")
            k9PunsjEventRepository.nullstillHistorikkvask()
            log.info("Nullstilt historikkvaskmarkering k9-punsj")
            HistorikkvaskMetrikker.observe(METRIKKLABEL, t0)
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvBehandlingProsessEventer(behandlingsIder: List<UUID>): Int {
        return behandlingsIder.sumOf { uuid -> vaskOgMarkerOppgaveForBehandlingUUID(uuid) }
    }

    @WithSpan
    fun vaskOgMarkerOppgaveForBehandlingUUID(uuid: UUID): Int {
        try {
            return transactionalManager.transaction { tx ->
                val eventTeller = vaskOppgaveForBehandlingUUID(uuid, tx)
                k9PunsjEventRepository.markerVasketHistorikk(uuid, tx)
                eventTeller
            }
        } catch (e: Exception) {
            log.warn("Historikkvask for $uuid fra punsj feilet", e)

            transactionalManager.transaction { tx ->
                //marker som vasket for å unngå evig løkke
                //manglende historikkvask må fanges opp fra WARNINGs i loggen
                k9PunsjEventRepository.markerVasketHistorikk(uuid, tx)
            }
            return 0;
        }
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID): Int {
        return transactionalManager.transaction { tx ->
            vaskOppgaveForBehandlingUUID(uuid, tx)
        }
    }

    private fun vaskOppgaveForBehandlingUUID(uuid: UUID, tx: TransactionalSession): Int {
        log.info("Vasker historikk for k9punsj-oppgave med eksternId: $uuid")
        var eventTeller = 0
        var forrigeOppgave: OppgaveV3? = null

        val behandlingProsessEventer: List<PunsjEventDto> = k9PunsjEventRepository.hentMedLås(tx, uuid).eventer
        var oppgaveV3: OppgaveV3? = null

        for (event in behandlingProsessEventer) {
            val oppgaveDto = PunsjEventTilOppgaveMapper.lagOppgaveDto(event, forrigeOppgave)

            oppgaveV3 = oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveDto, eventTeller, tx)
            oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveV3, eventTeller, tx)
            log.info("Oppdaterte eksisterende oppgaveversjon")

            forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                område = "K9",
                oppgavetype = K9Oppgavetypenavn.PUNSJ.kode,
                eksternId = oppgaveDto.eksternId,
                eksternVersjon = oppgaveDto.eksternVersjon,
                tx = tx
            )
            eventTeller++
        }

        oppgaveV3?.let {
            oppgaveV3Tjeneste.ajourholdOppgave(it, eventTeller, tx)
        }
        log.info("Vasket $eventTeller hendelser for k9punsj-oppgave med eksternId: $uuid")
        return eventTeller
    }

}
