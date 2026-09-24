package no.nav.k9.los.domeneadaptere.eventtiloppgave

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
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

        val eventerMedNummerering = hentEventerOgKorriger(eventnøkkel, tx, eventer)
        if (eventerMedNummerering.isEmpty()) return statistikktellerInn

        var statistikkteller = statistikktellerInn
        var forrigeOppgaveversjon = hentStartversjon(eventnøkkel, eventerMedNummerering, tx)
        var sisteOppgaveversjon: OppgaveV3? = null
        val sisteVersjonPerOppgavetype = mutableMapOf<String, Pair<Int, OppgaveV3>>()

        for ((eventnummer, eventLagret) in eventerMedNummerering) {
            val oppgave = mapOgLagre(eventLagret, eventnummer, forrigeOppgaveversjon, tx)
            if (oppgave != null) {
                // Kun i normalflyt: ny versjon er usendt til DVH inntil kvittert.
                // Historikkvask skal ikke trigge resend-semantikk for allerede sendte versjoner.
                statistikkRepository.bestillDvhSending(
                    eksternId = oppgave.eksternId,
                    eksternVersjon = oppgave.eksternVersjon,
                    oppgavetypeEksternId = oppgave.oppgavetype.eksternId,
                    tx = tx,
                )
                oppgaveOppdatertHandler.håndterOppgaveOppdatert(eventLagret, oppgave, tx)
                statistikkteller++
                forrigeOppgaveversjon = oppgave
                sisteOppgaveversjon = oppgave
            } else {
                forrigeOppgaveversjon = hentEksisterendeVersjon(eventnøkkel, eventLagret, eventnummer, tx)
            }
            forrigeOppgaveversjon?.let { sisteVersjonPerOppgavetype[it.oppgavetype.eksternId] = Pair(eventnummer, it) }
        }

        // Oppdater PEP-cache én gang for siste tilstand, i stedet for per event
        if (sisteOppgaveversjon != null) {
            oppgaveOppdatertHandler.oppdaterPepCache(sisteOppgaveversjon, tx)
        }

        fjernDirtyOgAjourhold(eventerMedNummerering, sisteVersjonPerOppgavetype, tx)
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
        val eventerMedNummerering = hentEventerOgKorriger(eventnøkkel, tx, eventer)
        if (eventerMedNummerering.isEmpty()) return 0L

        var statistikkteller = 0L
        var forrigeOppgaveversjon = hentStartversjon(eventnøkkel, eventerMedNummerering, tx)
        val sisteVersjonPerOppgavetype = mutableMapOf<String, Pair<Int, OppgaveV3>>()

        for ((eventnummer, eventLagret) in eventerMedNummerering) {
            val oppgave = mapOgLagre(eventLagret, eventnummer, forrigeOppgaveversjon, tx)
            if (oppgave != null) {
                statistikkteller++
                forrigeOppgaveversjon = oppgave
            } else {
                forrigeOppgaveversjon = hentEksisterendeVersjon(eventnøkkel, eventLagret, eventnummer, tx)
            }
            forrigeOppgaveversjon?.let { sisteVersjonPerOppgavetype[it.oppgavetype.eksternId] = Pair(eventnummer, it) }
        }

        fjernDirtyOgAjourhold(eventerMedNummerering, sisteVersjonPerOppgavetype, tx)
        return statistikkteller
    }

    private fun hentEventerOgKorriger(
        eventnøkkel: EventNøkkel,
        tx: TransactionalSession,
        eventer: List<EventLagret>? = null,
    ): List<Pair<Int, EventLagret>> {
        val låsteEventer = eventer ?: eventRepository.hentAlleEventerMedLås(eventnøkkel, tx)
        if (låsteEventer.isEmpty()) return emptyList()

        //TODO: Guard for å holde unna UPY inntil videre. Fjernes når UPY er klar for produksjon.
        val førsteEvent = låsteEventer.first()
        if (førsteEvent is EventLagret.UngSak && førsteEvent.eventDto.ytelseTypeKode == FagsakYtelseType.UNGDOMSYTELSE.kode) {
            return emptyList()
        }

        // Oppslag mot kildesystemene gjøres samlet for hele serien, slik at mappingen under er ren.
        val berikedeEventer = eventBeriker.berik(låsteEventer)
        return VaskeeventSerieutleder.nummererEventseriePerOppgavetype(berikedeEventer)
    }

    private fun hentStartversjon(
        eventnøkkel: EventNøkkel,
        eventerMedNummerering: List<Pair<Int, EventLagret>>,
        tx: TransactionalSession,
    ): OppgaveV3? {
        val førsteEventnummer = eventerMedNummerering.first().first
        // Første dirty melding er ikke første for oppgaven – hent foregående versjon som kontekst.
        return if (førsteEventnummer > 0) {
            hentEksisterendeVersjon(eventnøkkel, eventerMedNummerering.first().second, førsteEventnummer - 1, tx)
        } else {
            null
        }
    }

    private fun hentEksisterendeVersjon(
        eventnøkkel: EventNøkkel,
        eventLagret: EventLagret,
        internVersjon: Int,
        tx: TransactionalSession,
    ): OppgaveV3? {
        return oppgaveV3Tjeneste.hentOppgaveversjon(
            eventLagret.område,
            eventLagret.oppgavetypeKode(),
            eventnøkkel.eksternId,
            internVersjon,
            tx,
        )
    }

    private fun mapOgLagre(
        eventLagret: EventLagret,
        eventnummer: Int,
        forrigeOppgaveversjon: OppgaveV3?,
        tx: TransactionalSession,
    ): OppgaveV3? {
        val forrigeAvSammeOppgavetype = forrigeOppgaveversjon
            ?.takeIf { it.oppgavetype.eksternId == eventLagret.oppgavetypeKode() }
        val nyOppgaveversjon = eventLagret.tilOppgaveversjon(forrigeAvSammeOppgavetype, eventnummer)
        return oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(nyOppgaveversjon, tx, forrigeAvSammeOppgavetype)
    }

    private fun fjernDirtyOgAjourhold(
        eventerMedNummerering: List<Pair<Int, EventLagret>>,
        sisteVersjonPerOppgavetype: Map<String, Pair<Int, OppgaveV3>>,
        tx: TransactionalSession,
    ) {
        check(sisteVersjonPerOppgavetype.isNotEmpty()) {
            "Fant ingen oppgaveversjon å ajourholde for eksternId: ${eventerMedNummerering.first().second.eksternId}"
        }
        // Batch-oppdater alle dirty-flagg i én SQL-spørring i stedet for én pr event
        eventRepository.fjernAlleDirty(eventerMedNummerering.first().second.nøkkelId, tx)
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
