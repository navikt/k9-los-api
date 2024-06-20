package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.jobber.JobbMetrikker
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheService
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9PunsjTilLosAdapterTjeneste(
    private val eventRepository: PunsjEventK9Repository,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val pepCacheService: PepCacheService,
) {
    private val log: Logger = LoggerFactory.getLogger(K9PunsjTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-punsj-til-los"

    fun kjør(kjørSetup: Boolean = false, kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            when (kjørUmiddelbart) {
                true -> spillAvUmiddelbart()
                false -> schedulerAvspilling(kjørSetup)
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvUmiddelbart() {
        log.info("Spiller av BehandlingProsessEventer umiddelbart")
        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN
        ) {
            spillAvBehandlingProsessEventer()
        }
    }

    private fun schedulerAvspilling(kjørSetup: Boolean) {
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 2m fra nå, hver time")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.MINUTES.toMillis(2),
            period = TimeUnit.HOURS.toMillis(1)
        ) {
            if (kjørSetup) {
                setup()
            }
            try {
                JobbMetrikker.time("spill_av_behandlingprosesseventer_k9punsj") {
                    spillAvBehandlingProsessEventer()
                }
            } catch (e: Exception) {
                log.warn("Avspilling av k9punsj-eventer til oppgaveV3 feilet. Retry om en time", e)
            }
        }
    }

    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = eventRepository.hentAlleDirtyEventIder()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { uuid ->
            eventTeller = oppdaterOppgaveForEksternId(uuid, eventTeller)
            behandlingTeller++
            loggFremgangForHver100(behandlingTeller, "Forsert $behandlingTeller behandlinger")
        }

        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter kjøring: $antallAlle, antall aktive: $antallAktive, antall nye eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    fun oppdaterOppgaveForEksternId(uuid: UUID, eventTellerInn: Long = 0): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgaveversjon: OppgaveV3? = null

        transactionalManager.transaction { tx ->
            val punsjEventer = eventRepository.hentMedLås(tx, uuid)
            for (event in punsjEventer.eventer) {
                val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgaveversjon)
                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                if (oppgave != null) {
                    // TODO: Sjekk denne
//                    pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)

                    annullerReservasjonHvisPåVentEllerAvsluttet(oppgave, tx)
                    // Flere tilfeller som skal håndteres her?

                    eventTeller++
                }

                forrigeOppgaveversjon = oppgave
            }
            eventRepository.fjernDirty(uuid, tx)
        }

        return eventTeller
    }

    private fun annullerReservasjonHvisPåVentEllerAvsluttet(
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        if (oppgave.status == Oppgavestatus.LUKKET || oppgave.status == Oppgavestatus.VENTER) {
            reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                oppgave.reservasjonsnøkkel,
                "Maskinelt annullert reservasjon, siden oppgave på reservasjonen er avsluttet eller på vent",
                null,
                tx
            )
        }
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

    fun setup(): K9PunsjTilLosAdapterTjeneste {
        val objectMapper = jacksonObjectMapper()
        opprettOppgavetype(objectMapper)
        return this
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9PunsjTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9punsj.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(
            oppgavetyperDto.copy(
                oppgavetyper = oppgavetyperDto.oppgavetyper.map { oppgavetypeDto ->
                    oppgavetypeDto.copy(
                        oppgavebehandlingsUrlTemplate = oppgavetypeDto.oppgavebehandlingsUrlTemplate.replace(
                            "{baseUrl}",
                            config.k9FrontendUrl()
                        )
                    )
                }.toSet()
            )
        )
        log.info("opprettet oppgavetype")
    }
}
