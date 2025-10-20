package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class EventTilOppgaveAdapter(
    private val eventRepository: EventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val eventTilOppgaveMapper: EventTilOppgaveMapper,
    private val oppgaveOppdatertHandler: OppgaveOppdatertHandler,
) {
    private val log: Logger = LoggerFactory.getLogger(EventTilOppgaveAdapter::class.java)
    private val TRÅDNAVN = "event-til-oppgave"

    @WithSpan
    fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av K9-eventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val eventnøkler = eventRepository.hentAlleEksternIderMedDirtyEventer() //TODO: hente bare punsj, for pilottest
        log.info("Fant ${eventnøkler.size} eksternIder")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        eventnøkler.forEach { nøkkel ->
            eventTeller = oppdaterOppgaveForEksternId(nøkkel, eventTeller)
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
    fun oppdaterOppgaveForEksternId(@SpanAttribute eventnøkkel: EventNøkkel, eventTellerInn: Long = 0): Long {
        log.info("Nytt felles oppgaveadapter, oppdaterer oppgave for fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId}")
        var eventTeller = eventTellerInn
        var forrigeOppgaveversjon: OppgaveV3? = null

        transactionalManager.transaction { tx ->
            val eventerForEksternId = eventRepository.hentAlleDirtyEventerMedLås(eventnøkkel.fagsystem, eventnøkkel.eksternId, tx).sortedBy { LocalDateTime.parse(it.eksternVersjon) }

            sjekkMeldingIFeilRekkefølgeOgBestillVask(eventnøkkel, eventerForEksternId, tx)

            forrigeOppgaveversjon =
                oppgaveV3Tjeneste.hentOppgaveversjonenFør(
                    "K9",
                    K9Oppgavetypenavn.fraFagsystem(eventnøkkel.fagsystem).kode,
                    eventnøkkel.eksternId,
                    eventerForEksternId.first().eksternVersjon,
                    tx
                )
            for (eventLagret in eventerForEksternId) {
                val oppgaveDto = eventTilOppgaveMapper.mapOppgave(eventLagret, forrigeOppgaveversjon)
                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                if (oppgave != null) {
                    oppgaveOppdatertHandler.håndterOppgaveOppdatert(eventLagret, oppgave, tx)

                    eventTeller++
                    forrigeOppgaveversjon = oppgave
                } else { // hvis oppgave == null ble ikke oppgaven oppdatert selv om eventet var dirty. Vi henter ut oppgaveversjonen vi forsøkte å oppdatere som kontekst for neste event
                    forrigeOppgaveversjon = oppgaveV3Tjeneste.hentOppgaveversjonenFør(
                        "K9",
                        K9Oppgavetypenavn.fraFagsystem(eventLagret.fagsystem).kode,
                        eventLagret.eksternId,
                        eventLagret.eksternVersjon,
                        tx
                    )
                }
                eventRepository.fjernDirty(eventLagret, tx)
            }
        }
        return eventTeller
    }

    private fun sjekkMeldingIFeilRekkefølgeOgBestillVask(eventnøkkel: EventNøkkel, eventerForEksternId: List<EventLagret>, tx: TransactionalSession)  {
        log.info("Sjekker rekkefølge for eventnøkkel: $eventnøkkel")
        val sisteEksternVersjon =
            oppgaveV3Tjeneste.hentSisteEksternVersjon("K9", K9Oppgavetypenavn.fraFagsystem(eventnøkkel.fagsystem).kode, eventnøkkel.eksternId, tx)

        val meldingerIFeilRekkefølge = sisteEksternVersjon?.let {
            LocalDateTime.parse(sisteEksternVersjon)
                .isAfter(LocalDateTime.parse(eventerForEksternId.last().eksternVersjon))
        } ?: false //Logger enn så lenge, men trenger å trigge historikkvask
        if (meldingerIFeilRekkefølge) {
            log.warn("Oppgave med fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId} har fått meldinger i feil rekkefølge. Bestiller historikkvask!")
            eventRepository.bestillHistorikkvask(eventnøkkel.fagsystem, eventnøkkel.eksternId, tx)
        }
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
