package no.nav.k9.los.domeneadaptere.statistikk

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.domeneadaptere.statistikk.avp.AvpOppgaveTilDVHMapper
import no.nav.k9.los.domeneadaptere.statistikk.avp.AvpStatistikkPublisher
import no.nav.k9.los.domeneadaptere.statistikk.k9.K9Behandling
import no.nav.k9.los.domeneadaptere.statistikk.k9.K9KlageOppgaveTilDVHMapper
import no.nav.k9.los.domeneadaptere.statistikk.k9.K9SakOppgaveTilDVHMapper
import no.nav.k9.los.domeneadaptere.statistikk.k9.Sak
import no.nav.k9.los.domeneadaptere.statistikk.k9.K9StatistikkPublisher
import no.nav.k9.los.domeneadaptere.statistikk.ung.UngOppgaveTilDVHMapper
import no.nav.k9.los.domeneadaptere.statistikk.ung.UngStatistikkPublisher
import no.nav.k9.los.forvaltning.ForvaltningSensitiveObjectMapper
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.infrastruktur.db.Appversjonsvakt
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import org.slf4j.LoggerFactory

class OppgavestatistikkTjeneste(
    private val k9StatistikkPublisher: K9StatistikkPublisher,
    private val avpStatistikkPublisher: AvpStatistikkPublisher,
    private val ungStatistikkPublisher: UngStatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val pepCacheRepository: PepCacheRepository,
    private val appversjonsvakt: Appversjonsvakt,
    private val eventRepository: EventRepository,
) {

    private class PepCachePerSaksnummerState(
        private val pepCacheRepository: PepCacheRepository,
        private val område: Områder,
        private val maksAntallEksternIdPerSaksnummer: Int = 32,
    ) {
        private var gjeldendeSaksnummer: String? = null
        private val kode6PerOppgaveEksternId = object : LinkedHashMap<String, Boolean>(maksAntallEksternIdPerSaksnummer, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > maksAntallEksternIdPerSaksnummer
            }
        }

        fun hentEllerOppdater(saksnummer: String, oppgaveEksternId: String, tx: TransactionalSession): Boolean {
            if (gjeldendeSaksnummer != saksnummer) {
                gjeldendeSaksnummer = saksnummer
                kode6PerOppgaveEksternId.clear()
            }

            return kode6PerOppgaveEksternId.getOrPut(oppgaveEksternId) {
                pepCacheRepository.hent(område, oppgaveEksternId, tx)?.kode6 ?: false
            }
        }
    }

    private data class Oppgavestatistikkgrunnlag(
        val sak: Sak,
        val behandlinger: List<K9Behandling>,
        val oppgaveEksternId: String,
    )

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val k9SakMapper = K9SakOppgaveTilDVHMapper()
    private val k9KlageMapper = K9KlageOppgaveTilDVHMapper()
    private val avpMapper = AvpOppgaveTilDVHMapper()
    private val ungMapper = UngOppgaveTilDVHMapper()

    companion object {
        private const val FLUSH_INTERVAL = 100
    }

    fun spillAvUsendtStatistikk() {
        val feil = mutableListOf<Exception>()
        statistikkRepository.aktiveDvhOppgavetyper.forEach { (område, oppgavetyper) ->
            try {
                oppgavetyper.forEach { oppgavetype ->
                    spillAvUsendtStatistikk(oppgavetype)
                }
            } catch (e: Exception) {
                log.error("Sending av statistikk for område $område til DVH feilet", e)
                feil.add(e)
            }
        }
        if (feil.isNotEmpty()) {
            val første = feil.first()
            feil.drop(1).forEach { første.addSuppressed(it) }
            throw første
        }
    }

    private fun spillAvUsendtStatistikk(oppgavetype: Oppgavetype) {
        val område = oppgavetype.område
        log.info("Starter sending av statistikk for oppgavetype ${oppgavetype.kode} i område $område til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype)
        val pepCacheState = PepCachePerSaksnummerState(pepCacheRepository, område)
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner for oppgavetype ${oppgavetype.kode} i område $område som ikke er sendt til DVH")

        var antallSendt = 0
        for (bolk in oppgaverSomIkkeErSendt.chunked(FLUSH_INTERVAL)) {
            // Mapping, publisering, flush og kvittering i én transaksjon som holder versjonslåsen,
            // slik at en ny versjon venter til bolken er ferdig før den tar over.
            val sendte = transactionalManager.transaction { tx ->
                if (!appversjonsvakt.erAktiv(tx)) return@transaction null
                val klareForKvittering = bolk.filter { sendStatistikkAsynkront(it, pepCacheState, tx) }
                if (klareForKvittering.isNotEmpty()) {
                    flushOgKvitter(område, klareForKvittering, tx)
                }
                klareForKvittering
            }
            if (sendte == null) {
                log.warn("Avbryter sending av statistikk for oppgavetype ${oppgavetype.kode} i område $område etter $antallSendt oppgaveversjoner, appversjonen er ikke aktiv")
                return
            }
            antallSendt += sendte.size
            log.info("Sendt $antallSendt av ${oppgaverSomIkkeErSendt.size} oppgaveversjoner for oppgavetype ${oppgavetype.kode} i område $område")
        }

        val kjoretid = System.currentTimeMillis() - tidStatistikksendingStartet
        log.info("Sending av statistikk for oppgavetype ${oppgavetype.kode} i område $område ferdig")
        log.info("Sendt $antallSendt av ${oppgaverSomIkkeErSendt.size} oppgaveversjoner for oppgavetype ${oppgavetype.kode} i område $område. Totalt tidsbruk: ${kjoretid} ms")
        if (antallSendt > 0) {
            log.info("Gjennomsnitt tidsbruk: ${kjoretid / antallSendt} ms pr oppgaveversjon")
        }
    }

    @WithSpan
    private fun sendStatistikkAsynkront(
        oppgaveversjon: DvhOppgaveversjon,
        pepCacheState: PepCachePerSaksnummerState,
        tx: TransactionalSession,
    ): Boolean {
        eventRepository.hentOgLåsEventnøkkel(oppgaveversjon.oppgavetype.fagsystem, oppgaveversjon.eksternId, tx)
        val (oppgave, versjon) = statistikkRepository.hentOppgaveForSending(tx, oppgaveversjon) ?: return false
        when (oppgave.område) {
            Områder.K9 -> sendK9Statistikk(oppgave, versjon, pepCacheState, tx)
            Områder.AKTIVITETSPENGER -> sendAvpStatistikk(oppgave, pepCacheState, tx)
            Områder.UNGDOMSPROGRAMYTELSEN -> sendUngStatistikk(oppgave, pepCacheState, tx)
        }
        return true
    }

    private fun sendAvpStatistikk(oppgave: Oppgave, pepCacheState: PepCachePerSaksnummerState, tx: TransactionalSession) {
        val erKode6 = pepCacheState.hentEllerOppdater(
            saksnummer = oppgave.hentVerdi("saksnummer")!!,
            oppgaveEksternId = oppgave.eksternId,
            tx = tx,
        )
        if (erKode6) return

        val behandling = avpMapper.lagBehandling(oppgave)
        if (log.isDebugEnabled) {
            log.debug(
                "Utgående AvpBehandling: {}",
                ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(behandling),
            )
        }
        avpStatistikkPublisher.publiserAsynkront(behandling)
    }

    private fun sendUngStatistikk(oppgave: Oppgave, pepCacheState: PepCachePerSaksnummerState, tx: TransactionalSession) {
        val behandling = ungMapper.lagBehandling(oppgave)
        val erKode6 = pepCacheState.hentEllerOppdater(
            saksnummer = oppgave.hentVerdi("saksnummer")!!,
            oppgaveEksternId = oppgave.eksternId,
            tx = tx,
        )
        val behandlingTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(behandling) else behandling
        if (log.isDebugEnabled) {
            log.debug(
                "Utgående UngBehandling: {}",
                ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(behandlingTilSending),
            )
        }
        ungStatistikkPublisher.publiserAsynkront(behandlingTilSending)
    }

    private fun sendK9Statistikk(
        oppgave: Oppgave,
        versjon: Int,
        pepCacheState: PepCachePerSaksnummerState,
        tx: TransactionalSession,
    ) {
        val oppgavestatistikkgrunnlag = byggOppgavestatistikk(oppgave, versjon)

        val erKode6 = pepCacheState.hentEllerOppdater(
            saksnummer = oppgavestatistikkgrunnlag.sak.saksnummer,
            oppgaveEksternId = oppgavestatistikkgrunnlag.oppgaveEksternId,
            tx = tx,
        )

        val sakTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(oppgavestatistikkgrunnlag.sak) else oppgavestatistikkgrunnlag.sak
        oppgavestatistikkgrunnlag.behandlinger.forEach {
            val behandlingTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(it) else it
            if (log.isDebugEnabled) {
                log.debug(
                    "Utgående DvhBehandling: {}",
                    ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(behandlingTilSending),
                )
            }
            k9StatistikkPublisher.publiserAsynkront(sakTilSending, behandlingTilSending)
        }
    }

    private fun flushOgKvitter(område: Områder, oppgaveversjonerTilKvittering: List<DvhOppgaveversjon>, tx: TransactionalSession) {
        when (område) {
            Områder.K9 -> k9StatistikkPublisher.flushOgValider()
            Områder.AKTIVITETSPENGER -> avpStatistikkPublisher.flushOgValider()
            Områder.UNGDOMSPROGRAMYTELSEN -> ungStatistikkPublisher.flushOgValider()
        }
        oppgaveversjonerTilKvittering.forEach { oppgaveversjon ->
            statistikkRepository.kvitterSending(tx, oppgaveversjon)
        }
    }

    private fun <T : Any> nullUtEventuelleSensitiveFelter(value: T): T = SensitiveFieldCensor.censor(value)

    private fun byggOppgavestatistikk(oppgave: Oppgave, versjon: Int): Oppgavestatistikkgrunnlag {
        return when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> Oppgavestatistikkgrunnlag(
                sak = k9SakMapper.lagSak(oppgave),
                behandlinger = k9SakMapper.lagBehandlinger(oppgave, versjon),
                oppgaveEksternId = oppgave.eksternId,
            )
            "k9klage" -> Oppgavestatistikkgrunnlag(
                sak = k9KlageMapper.lagSak(oppgave),
                behandlinger = listOf(k9KlageMapper.lagBehandling(oppgave)),
                oppgaveEksternId = oppgave.eksternId,
            )
            else -> throw IllegalStateException("Ukjent oppgavetype for sending til DVH: ${oppgave.oppgavetype.eksternId}")
        }
    }

    fun slettStatistikkgrunnlag(oppgavetype: String) {
        statistikkRepository.bestillDvhSending(oppgavetype)
    }
}