package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.JobbMetrikker
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9PunsjTilLosAdapterTjenestePerLinje(
    private val eventRepository: EventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val pepCacheService: PepCacheService,
) {
    private val log: Logger = LoggerFactory.getLogger(K9PunsjTilLosAdapterTjenestePerLinje::class.java)
    private val TRÅDNAVN = "k9-punsj-til-los"

    fun kjør(kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            when (kjørUmiddelbart) {
                true -> spillAvUmiddelbart()
                false -> schedulerAvspilling()
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

    private fun schedulerAvspilling() {
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 3m fra nå, hver time")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.MINUTES.toMillis(3),
            period = TimeUnit.HOURS.toMillis(1)
        ) {
            try {
                JobbMetrikker.time("spill_av_behandlingprosesseventer_k9punsj") {
                    spillAvBehandlingProsessEventer()
                }
            } catch (e: Exception) {
                log.warn("Avspilling av k9punsj-eventer til oppgaveV3 feilet. Retry om en time", e)
            }
        }
    }

    @WithSpan
    private fun spillAvBehandlingProsessEventer() {

        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = eventRepository.hentAlleEksternIderMedDirtyEventer(Fagsystem.PUNSJ)
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { eksternId ->
            eventTeller = oppdaterOppgaveForEksternId(eksternId, eventTeller)
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

    @WithSpan
    fun oppdaterOppgaveForEksternId(@SpanAttribute eksternId: String, eventTellerInn: Long = 0): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgaveversjon: OppgaveV3? = null

        transactionalManager.transaction { tx ->
            val punsjEventer = eventRepository.hentAlleDirtyEventerMedLås(eksternId, Fagsystem.PUNSJ, tx).sortedBy { LocalDateTime.parse(it.eksternVersjon) }

            val sisteEksternVersjon =
                oppgaveV3Tjeneste.hentSisteEksternVersjon("K9", K9Oppgavetypenavn.PUNSJ.kode, eksternId, tx)

            val meldingerIFeilRekkefølge = sisteEksternVersjon?.let {
                LocalDateTime.parse(sisteEksternVersjon)
                    .isAfter(LocalDateTime.parse(punsjEventer.last().eksternVersjon))
            } ?: false //Logger enn så lenge, men trenger å trigge historikkvask
            if (meldingerIFeilRekkefølge) {
                log.error("Punsjoppgave med eksternId: $eksternId har fått meldinger i feil rekkefølge. Må historikkvaskes!")
            }

            forrigeOppgaveversjon =
                oppgaveV3Tjeneste.hentOppgaveversjonenFør(
                    "K9",
                    "k9-punsj",
                    eksternId,
                    punsjEventer.first().eksternVersjon,
                    tx
                )
            for (eventLagret in punsjEventer) {
                val punsjEvent = PunsjEventDto.fraEventLagret(eventLagret)
                val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(punsjEvent, forrigeOppgaveversjon)
                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                if (oppgave != null) {
                    pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)

                    annullerReservasjonHvisPåVentEllerAvsluttet(oppgave, tx)
                    // Flere tilfeller som skal håndteres her?

                    eventTeller++
                    forrigeOppgaveversjon = oppgave
                } else { // hvis oppgave == null ble ikke oppgaven oppdatert selv om eventet var dirty. Vi henter ut oppgaveversjonen vi forsøkte å oppdatere som kontekst for neste event
                    forrigeOppgaveversjon = oppgaveV3Tjeneste.hentOppgaveversjonenFør(
                        "K9",
                        K9Oppgavetypenavn.PUNSJ.kode,
                        eksternId,
                        eventLagret.eksternVersjon,
                        tx
                    )
                }
                eventRepository.fjernDirty(eventLagret, tx)
            }
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
}
