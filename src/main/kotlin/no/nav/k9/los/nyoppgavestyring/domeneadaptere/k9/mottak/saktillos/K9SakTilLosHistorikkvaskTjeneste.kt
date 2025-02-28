package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos

import kotlinx.coroutines.*
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.eventhandler.DetaljerMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.HistorikkvaskMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class K9SakTilLosHistorikkvaskTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
    private val eventTilDtoMapper: EventTilDtoMapper
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosHistorikkvaskTjeneste::class.java)

    private val METRIKKLABEL = "k9-sak-til-los-historikkvask"

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9sak-hendelser")

            val dispatcher = newFixedThreadPoolContext(5, "Historikkvask k9sak")

            val tidKjøringStartet = System.currentTimeMillis()
            var t0 = System.nanoTime()
            var eventTeller = 0L
            var behandlingTeller = 0L
            val antallEventIder = behandlingProsessEventK9Repository.hentAntallEventIderUtenVasketHistorikk()
            log.info("Fant totalt $antallEventIder behandlingsider som skal rekjøres mot oppgavemodell")

            while (true) {
                val behandlingsIder = DetaljerMetrikker.time(
                    "k9sakHistorikkvask",
                    "hentBehandlinger"
                ) { behandlingProsessEventK9Repository.hentAlleEventIderUtenVasketHistorikk(antall = 2000) }
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
            log.info("Antall oppgaver etter historikkvask (k9-sak): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")

            val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
            if (eventTeller > 0) {
                log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
            }

            log.info("Historikkvask k9sak ferdig")
            nullstillhistorikkvask()
            HistorikkvaskMetrikker.observe(METRIKKLABEL, t0)

        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun nullstillhistorikkvask() {
        behandlingProsessEventK9Repository.nullstillHistorikkvask()
        log.info("Nullstilt historikkvaskmarkering k9-sak")
    }

    private fun spillAvBehandlingProsessEventer(
        dispatcher: ExecutorCoroutineDispatcher,
        behandlingsIder: List<UUID>
    ): Long {
        val scope = CoroutineScope(dispatcher)

        val jobber = behandlingsIder.map {
            //bruker run blocking for å sikre at tråden(e) som kjører vasking gjør seg ferdig med en behandling uten å suspendere
            scope.async { runBlocking { vaskOppgaveForBehandlingUUIDOgMarkerVasket(it) } }
        }.toList()

        val eventTeller = runBlocking {
            jobber.map { it.await() }.sum()
        }
        return eventTeller
    }

    private fun vaskOppgaveForBehandlingUUIDOgMarkerVasket(uuid: UUID): Long {
        var eventTeller = 0L
        DetaljerMetrikker.time("k9sakHistorikkvask", "vaskOppgaveForBehandlingKomplett") {
            val nyeBehandlingsopplysningerFraK9Sak = DetaljerMetrikker.time(
                "k9sakHistorikkvask",
                "hentOpplysningerFraK9sak"
            ) { k9SakBerikerKlient.hentBehandling(UUID.fromString(uuid.toString()), antallForsøk = 8) }
            transactionalManager.transaction { tx ->
                eventTeller = DetaljerMetrikker.time(
                    "k9sakHistorikkvask",
                    "vaskOppgaveForBehandling"
                ) { vaskOppgaveForBehandlingUUID(uuid, nyeBehandlingsopplysningerFraK9Sak, tx) }
                behandlingProsessEventK9Repository.markerVasketHistorikk(uuid, tx)
            }
        }
        return eventTeller
    }

    fun vaskOppgaveForBehandlingUUID(uuid: UUID): Long {
        return DetaljerMetrikker.time("k9sakHistorikkvask", "vaskOppgaveForBehandling") {
            val nyeBehandlingsopplysningerFraK9Sak = DetaljerMetrikker.time(
                "k9sakHistorikkvask",
                "hentOpplysningerFraK9sak"
            ) { k9SakBerikerKlient.hentBehandling(UUID.fromString(uuid.toString()), antallForsøk = 8) }
            transactionalManager.transaction { tx ->
                vaskOppgaveForBehandlingUUID(uuid, nyeBehandlingsopplysningerFraK9Sak, tx)
            }
        }
    }

    fun vaskOppgaveForBehandlingUUID(
        uuid: UUID,
        nyeBehandlingsopplysningerFraK9Sak: BehandlingMedFagsakDto?,
        tx: TransactionalSession
    ): Long {
        log.info("Vasker historikk for k9sak-oppgave med eksternId: $uuid")
        var forrigeOppgave: OppgaveV3? = null

        val behandlingProsessEventer = DetaljerMetrikker.time("k9sakHistorikkvask", "hentEventer") {
            behandlingProsessEventK9Repository.hentMedLås(
                tx,
                uuid
            ).eventer
        }
        val høyesteInternVersjon = DetaljerMetrikker.time("k9sakHistorikkvask", "hentHøyesteInternVersjon") {
            oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9sak", "K9", tx)!!
        }
        var eventNrForBehandling = 0L
        var oppgaveV3: OppgaveV3? = null
        for (e in behandlingProsessEventer) {
            var event = e
            if (eventNrForBehandling > høyesteInternVersjon) {
                log.info("Avbryter historikkvask for ${event.eksternId} ved eventTid ${event.eventTid}. Forventer at håndteres av vanlig adaptertjeneste.")
                break //Historikkvasken har funnet eventer som ennå ikke er lastet inn med normalflyt. Dirty eventer skal håndteres av vanlig adaptertjeneste
            }
            if (event.eldsteDatoMedEndringFraSøker == null && nyeBehandlingsopplysningerFraK9Sak != null && nyeBehandlingsopplysningerFraK9Sak.eldsteDatoMedEndringFraSøker != null) {
                event =
                    event.copy(eldsteDatoMedEndringFraSøker = nyeBehandlingsopplysningerFraK9Sak.eldsteDatoMedEndringFraSøker)
                //ser ut som noen gamle mottatte dokumenter kan mangle innsendingstidspunkt.
                //da faller vi tilbake til å bruke behandling_opprettet i mapperen
            }
            var oppgaveDto = eventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)

            oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
                event,
                oppgaveDto,
                nyeBehandlingsopplysningerFraK9Sak
            )

            oppgaveV3 = DetaljerMetrikker.time(
                "k9sakHistorikkvask",
                "utledEksisterendeOppgaveversjon"
            ) { oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, tx) }
            DetaljerMetrikker.time(
                "k9sakHistorikkvask",
                "oppdaterEksisterendeOppgaveversjon"
            ) { oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveV3, eventNrForBehandling, tx) }

            forrigeOppgave = DetaljerMetrikker.time("k9sakHistorikkvask", "hentOppgaveversjon") {
                oppgaveV3Tjeneste.hentOppgaveversjon(
                    område = "K9", eksternId = oppgaveDto.id, eksternVersjon = oppgaveDto.versjon, tx = tx
                )
            }
            eventNrForBehandling++
        }

        oppgaveV3?.let {
            val sakstypekodefraK9sakKall = nyeBehandlingsopplysningerFraK9Sak?.sakstype?.kode
            val ytelsetypefraOppgaven =
                oppgaveV3.felter.filter { it.oppgavefelt.feltDefinisjon.eksternId == "ytelsestype" }.map { it.verdi }
                    .firstOrNull()
            log.info("sakstype fra kall er $sakstypekodefraK9sakKall og fra oppgaven er det $ytelsetypefraOppgaven")
            if (sakstypekodefraK9sakKall == no.nav.k9.kodeverk.behandling.FagsakYtelseType.FRISINN.kode || ytelsetypefraOppgaven == no.nav.k9.kodeverk.behandling.FagsakYtelseType.FRISINN.kode) {
                log.info("oppgave ${oppgaveV3.eksternId} gjelder FRISINN, ignorerer oppgaven")
            } else {
                DetaljerMetrikker.time(
                    "k9sakHistorikkvask",
                    "ajourholdAktivOppgave"
                ) { oppgaveV3Tjeneste.ajourholdAktivOppgave(oppgaveV3, eventNrForBehandling, tx) }
            }
        }
        log.info("Vasket $eventNrForBehandling hendelser for k9sak-oppgave med eksternId: $uuid")
        return eventNrForBehandling
    }
}
