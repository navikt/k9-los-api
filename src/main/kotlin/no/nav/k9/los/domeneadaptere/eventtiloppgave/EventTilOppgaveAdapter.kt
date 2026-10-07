package no.nav.k9.los.domeneadaptere.eventtiloppgave

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.domeneadaptere.statistikk.StatistikkRepository
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavemottak.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.OppgaveV3Tjeneste
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class EventTilOppgaveAdapter(
    private val eventRepository: EventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val eventBeriker: EventBeriker,
    private val oppgaveOppdatertHandler: OppgaveOppdatertHandler,
    private val ajourholdTjeneste: AktivOgPartisjonertOppgaveAjourholdTjeneste,
    private val statistikkRepository: StatistikkRepository,
) {
    private val log: Logger = LoggerFactory.getLogger(EventTilOppgaveAdapter::class.java)

    @WithSpan
    fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av K9-eventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val eventnøkler = eventRepository.hentAlleEksternIderMedDirtyEventer()
        log.info("Fant ${eventnøkler.size} eksternIder")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        eventnøkler.forEach { nøkkel ->
            eventTeller =
                try {
                    oppdaterOppgaveForEksternId(nøkkel, eventTeller)
                } catch (e: Exception) {
                    log.error("Oppgavevaktmester: Feil ved oppdatering av oppgave for fagsystem: ${nøkkel.fagsystem}, eksternId: ${nøkkel.eksternId}", e)
                    eventTeller
                }
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
    fun oppdaterOppgaveForEksternId(
        @SpanAttribute eventnøkkel: EventNøkkel,
        statistikktellerInn: Long = 0,
        eventer: List<EventLagret>? = null,
    ): Long {
        return transactionalManager.transaction { tx ->
            oppdaterOppgaveForEksternId(eventnøkkel, tx, statistikktellerInn, eventer)
        }
    }

    @WithSpan
    fun oppdaterOppgaveForEksternId(
        eventnøkkel: EventNøkkel,
        tx: TransactionalSession,
        statistikktellerInn: Long = 0,
        eventer: List<EventLagret>? = null,
    ): Long {
        log.info("Oppdaterer oppgave for fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId}")
        val eventhandlinger = hentEventhandlinger(eventnøkkel, tx, eventer, hoppOverUngdomsytelse = true)
        if (eventhandlinger.isEmpty()) return statistikktellerInn

        var statistikkteller = statistikktellerInn
        var sisteOppgaveversjon: OppgaveV3? = null
        val sisteVersjonPerOppgavetype = mutableMapOf<Oppgavetype, Pair<Int, OppgaveV3>>()

        for (eventHandling in eventhandlinger) {
            val forrigeOppgaveversjon = hentForrigeVersjon(eventnøkkel, eventHandling, sisteVersjonPerOppgavetype, tx)
            val oppgave = mapOgLagre(eventHandling, forrigeOppgaveversjon, tx)
            if (oppgave != null) {
                // Kun i normalflyt: ny versjon er usendt til DVH inntil kvittert.
                // Historikkvask skal ikke trigge resend-semantikk for allerede sendte versjoner.
                statistikkRepository.bestillDvhSending(
                    eksternId = oppgave.eksternId,
                    eksternVersjon = oppgave.eksternVersjon,
                    oppgavetypeEksternId = oppgave.oppgavetype.eksternId,
                    tx = tx,
                )
                oppgaveOppdatertHandler.håndterOppgaveOppdatert(eventHandling.event, oppgave, tx)
                statistikkteller++
                sisteOppgaveversjon = oppgave
            }
            (oppgave ?: hentEksisterendeVersjon(eventnøkkel, eventHandling, eventHandling.eventnummer, tx))
                ?.let { sisteVersjonPerOppgavetype[eventHandling.oppgavetype] = Pair(eventHandling.eventnummer, it) }
        }

        // Oppdater PEP-cache én gang for siste tilstand, i stedet for per event
        if (sisteOppgaveversjon != null) {
            oppgaveOppdatertHandler.oppdaterPepCache(sisteOppgaveversjon, tx)
        }

        fjernDirtyOgAjourhold(eventhandlinger, sisteVersjonPerOppgavetype, tx)
        return statistikkteller
    }

    /**
     * Variant for historikkvask. Forutsetter at kaller har slettet oppgave_v3 og satt eventene
     * dirty først. Skiller seg fra normalflyt på to punkter:
     *  - Hopper over rekkefølge-sjekk (oppgave_v3 er per definisjon tom).
     *  - Kjører ikke side-effekter (PEP-cache, køpåvirkende hendelser, reservasjons-
     *    håndtering) – vask skal være en stille rebuild.
     */
    fun oppdaterOppgaveForEksternIdUnderHistorikkvask(
        eventnøkkel: EventNøkkel,
        tx: TransactionalSession,
        eventer: List<EventLagret>? = null,
    ): Long {
        log.info("Vasker oppgave for fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId}")
        val eventhandlinger = hentEventhandlinger(eventnøkkel, tx, eventer)
        if (eventhandlinger.isEmpty()) return 0L

        var statistikkteller = 0L
        val sisteVersjonPerOppgavetype = mutableMapOf<Oppgavetype, Pair<Int, OppgaveV3>>()

        for (eventHandling in eventhandlinger) {
            val forrigeOppgaveversjon = hentForrigeVersjon(eventnøkkel, eventHandling, sisteVersjonPerOppgavetype, tx)
            val oppgave = mapOgLagre(eventHandling, forrigeOppgaveversjon, tx)
            if (oppgave != null) {
                statistikkteller++
            }
            (oppgave ?: hentEksisterendeVersjon(eventnøkkel, eventHandling, eventHandling.eventnummer, tx))
                ?.let { sisteVersjonPerOppgavetype[eventHandling.oppgavetype] = Pair(eventHandling.eventnummer, it) }
        }

        fjernDirtyOgAjourhold(eventhandlinger, sisteVersjonPerOppgavetype, tx)
        return statistikkteller
    }

    private fun hentEventhandlinger(
        eventnøkkel: EventNøkkel,
        tx: TransactionalSession,
        eventer: List<EventLagret>? = null,
        hoppOverUngdomsytelse: Boolean = false,
    ): List<NummerertEventHandling> {
        val låsteEventer = eventer ?: eventRepository.hentAlleEventerMedLås(eventnøkkel, tx)
        // Oppslag mot kildesystemene gjøres samlet for hele serien, slik at mappingen under er ren.
        val berikedeEventer = eventBeriker.berik(låsteEventer)
        // TODO: Hold unna UPY inntil videre. Fjernes når UPY er klar for produksjon.
        val førsteEvent = berikedeEventer.firstOrNull()
        if (hoppOverUngdomsytelse &&
            førsteEvent is EventLagret.UngSak &&
            førsteEvent.eventDto.ytelseTypeKode == FagsakYtelseType.UNGDOMSYTELSE.kode
        ) {
            return emptyList()
        }
        return utledEventhandlinger(berikedeEventer)
    }

    private fun hentForrigeVersjon(
        eventnøkkel: EventNøkkel,
        eventHandling: NummerertEventHandling,
        sisteVersjonPerOppgavetype: Map<Oppgavetype, Pair<Int, OppgaveV3>>,
        tx: TransactionalSession,
    ): OppgaveV3? {
        return sisteVersjonPerOppgavetype[eventHandling.oppgavetype]?.second
            ?: if (eventHandling.eventnummer > 0) {
                hentEksisterendeVersjon(eventnøkkel, eventHandling, eventHandling.eventnummer - 1, tx)
            } else {
                null
            }
    }

    private fun hentEksisterendeVersjon(
        eventnøkkel: EventNøkkel,
        eventHandling: NummerertEventHandling,
        internVersjon: Int,
        tx: TransactionalSession,
    ): OppgaveV3? {
        return oppgaveV3Tjeneste.hentOppgaveversjon(
            eventHandling.event.område,
            eventHandling.oppgavetype.kode,
            eventnøkkel.eksternId,
            internVersjon,
            tx,
        )
    }

    private fun mapOgLagre(
        eventhandling: NummerertEventHandling,
        forrigeOppgaveversjon: OppgaveV3?,
        tx: TransactionalSession,
    ): OppgaveV3? {
        check(forrigeOppgaveversjon == null || forrigeOppgaveversjon.oppgavetype.eksternId == eventhandling.oppgavetype.kode) {
            "Forrige oppgaveversjon har type ${forrigeOppgaveversjon?.oppgavetype?.eksternId}, forventet ${eventhandling.oppgavetype.kode}"
        }
        val innsending = when (eventhandling) {
            is NummerertEventHandling.MapEventHandling -> eventhandling.event.tilOppgaveversjon(forrigeOppgaveversjon, eventhandling.eventnummer)
            is NummerertEventHandling.LukkOppgavetype -> eventhandling.event.lukkOppgaveversjon(
                checkNotNull(forrigeOppgaveversjon) {
                    "Fant ingen oppgaveversjon av type ${eventhandling.oppgavetype} å lukke for eksternId: ${eventhandling.event.eksternId}"
                }
            )
        }
        check(innsending.dto.type.kode == eventhandling.oppgavetype.kode) {
            "Innsending har type ${innsending.dto.type.kode}, forventet ${eventhandling.oppgavetype.kode}"
        }
        return oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innsending, tx, forrigeOppgaveversjon)
    }

    private fun fjernDirtyOgAjourhold(
        nummerertEventHandling: List<NummerertEventHandling>,
        sisteVersjonPerOppgavetype: Map<Oppgavetype, Pair<Int, OppgaveV3>>,
        tx: TransactionalSession,
    ) {
        check(sisteVersjonPerOppgavetype.isNotEmpty()) {
            "Fant ingen oppgaveversjon å ajourholde for eksternId: ${nummerertEventHandling.first().event.eksternId}"
        }
        // Batch-oppdater alle dirty-flagg i én SQL-spørring i stedet for én pr event
        eventRepository.fjernAlleDirty(nummerertEventHandling.first().event.nøkkelId, tx)
        // Kjøres alltid som sikkerhetsnett: ajourhold er også del av vanlig event-ingest, og
        // koster lite hvis staten faktisk er uendret.
        sisteVersjonPerOppgavetype.values.forEach { (eventnummer, sluttversjon) ->
            ajourholdTjeneste.ajourholdOppgave(sluttversjon, eventnummer, tx)
        }
    }


    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
