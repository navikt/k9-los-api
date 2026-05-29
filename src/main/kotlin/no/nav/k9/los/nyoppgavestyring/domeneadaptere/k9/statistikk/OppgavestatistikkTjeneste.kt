package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import org.slf4j.LoggerFactory

class OppgavestatistikkTjeneste(
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val pepCacheRepository: PepCacheRepository
) {

    private data class Oppgavestatistikkgrunnlag(
        val sak: Sak,
        val behandlinger: List<Behandling>,
        val oppgaveEksternId: String,
    )

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val k9SakMapper = K9SakOppgaveTilDVHMapper()
    private val k9KlageMapper = K9KlageOppgaveTilDVHMapper()

    companion object {
        private const val BATCH_SIZE = 200
    }

    fun spillAvUsendtStatistikk() {
        log.info("Starter sending av saks- og behandlingsstatistikk til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner som ikke er sendt til DVH")

        var antallSendt = 0
        oppgaverSomIkkeErSendt.chunked(BATCH_SIZE).forEach { batch ->
            sendStatistikkBatch(batch)
            antallSendt += batch.size
            log.info("Sendt $antallSendt av ${oppgaverSomIkkeErSendt.size} eventer")
        }

        val kjoretid = System.currentTimeMillis() - tidStatistikksendingStartet
        log.info("Sending av saks- og behandlingsstatistikk ferdig")
        log.info("Sendt ${oppgaverSomIkkeErSendt.size} oppgaveversjoner. Totalt tidsbruk: ${kjoretid} ms")
        if (oppgaverSomIkkeErSendt.isNotEmpty()) {
            log.info("Gjennomsnitt tidsbruk: ${kjoretid / oppgaverSomIkkeErSendt.size} ms pr oppgaveversjon")
        }
    }

    @WithSpan
    private fun sendStatistikkBatch(
        @SpanAttribute oppgaveIder: List<Long>,
    ) {
        // Hent alle oppgaver og felter i bulk innenfor én transaksjon
        val oppgaveData = transactionalManager.transaction { tx ->
            val oppgaverMedVersjon = statistikkRepository.hentOppgaverForIder(tx, oppgaveIder)

            // Bygg alle oppgavestatistikkgrunnlag
            val grunnlagPerOppgaveId = oppgaverMedVersjon.map { (oppgaveId, oppgaveOgVersjon) ->
                oppgaveId to byggOppgavestatistikk(oppgaveOgVersjon)
            }

            // Batch-hent kode6-status for alle unike eksternIder
            val alleEksternIder = grunnlagPerOppgaveId.map { it.second.oppgaveEksternId }.toSet()
            val pepCacheBatch = pepCacheRepository.hentBatch("K9", alleEksternIder, tx)

            // Bygg meldinger for sending
            val meldingerTilSending = mutableListOf<Pair<Sak, Behandling>>()
            for ((_, grunnlag) in grunnlagPerOppgaveId) {
                val erKode6 = pepCacheBatch[grunnlag.oppgaveEksternId]?.kode6 ?: false
                val sakTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(grunnlag.sak) else grunnlag.sak
                for (behandling in grunnlag.behandlinger) {
                    val behandlingTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(behandling) else behandling
                    if (log.isDebugEnabled) {
                        log.debug("Utgående DvhBehandling: {}", behandlingTilSending.tryggToString())
                    }
                    meldingerTilSending.add(sakTilSending to behandlingTilSending)
                }
            }

            meldingerTilSending
        }

        // Send alle Kafka-meldinger i batch (asynkront med samlet flush)
        statistikkPublisher.publiserBatch(oppgaveData)

        // Kvitter alle i én transaksjon
        transactionalManager.transaction { tx ->
            statistikkRepository.kvitterSendingBatch(tx, oppgaveIder)
        }
    }

    private fun nullUtEventuelleSensitiveFelter(sak: Sak): Sak {
        return sak.copy(aktorer = sak.aktorer.map { Aktør(aktorId = -5, rolle = "-5", rolleBeskrivelse = "-5") })
    }

    private fun nullUtEventuelleSensitiveFelter(behandling: Behandling): Behandling {
        return behandling.copy(
            beslutter = "-5",
            saksbehandler = "-5",
            behandlingOpprettetAv = "-5",
            ansvarligEnhetKode = "-5"
        )
    }

    private fun byggOppgavestatistikk(oppgaveOgVersjon: Pair<Oppgave, Int>): Oppgavestatistikkgrunnlag {
        val (oppgave, versjon) = oppgaveOgVersjon

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
        statistikkRepository.fjernSendtMarkering(oppgavetype)
    }
}