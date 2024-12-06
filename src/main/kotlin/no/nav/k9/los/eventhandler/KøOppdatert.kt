package no.nav.k9.los.eventhandler

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.launch
import no.nav.k9.kodeverk.Fagsystem
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

fun CoroutineScope.køOppdatertProsessor(
    channel: ReceiveChannel<UUID>,
    refreshOppgaveChannel: Channel<UUID>,
    oppgaveKøRepository: OppgaveKøRepository,
    oppgaveRepository: OppgaveRepository,
    oppgaveRepositoryV2: OppgaveRepositoryV2,
    oppgaveTjeneste: OppgaveTjeneste
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    val log = LoggerFactory.getLogger("behandleOppgave")
    for (uuid in channel) {
        try {
            ChannelMetrikker.timeSuspended("oppdaterKoe") {
                val measureTimeMillis = oppdaterKø(
                    oppgaveKøRepository,
                    uuid,
                    oppgaveRepository,
                    oppgaveRepositoryV2,
                    oppgaveTjeneste,
                    refreshOppgaveChannel,
                    log
                )
                log.info("tok ${measureTimeMillis}ms å oppdatere køen: $uuid")
            }
        } catch (e: Exception) {
            log.error("Feilet ved oppdatering av kø $uuid", e)
        }
    }
}

@WithSpan(value = "koOppdatertProsessor.oppdaterKo")
private suspend fun oppdaterKø(
    oppgaveKøRepository: OppgaveKøRepository,
    @SpanAttribute("koUuid") it: UUID,
    oppgaveRepository: OppgaveRepository,
    oppgaveRepositoryV2: OppgaveRepositoryV2,
    oppgaveTjeneste: OppgaveTjeneste,
    refreshOppgaveChannel: Channel<UUID>,
    log: Logger
): Long {
    return measureTimeMillis {
        val kø = oppgaveKøRepository.hentOppgavekø(it, ignorerSkjerming = true)
        val opprinnelige = kø.oppgaverOgDatoer.toMutableList()

        val aktiveOppgaver = oppgaveRepository.hentAktiveUreserverteOppgaver()

        kø.oppgaverOgDatoer.clear()
        for (oppgave in aktiveOppgaver) {
            if (kø.kode6 == oppgave.kode6) {
                kø.leggOppgaveTilEllerFjernFraKø(
                    oppgave = oppgave,
                    erOppgavenReservertSjekk = {false}, //har spesifikt hentet ureserverte oppgaver over
                )
            }
        }
        val k9sakRefreshBehanderListe = mutableListOf<BehandlingIdDto>()
        oppgaveKøRepository.lagreInkluderKode6(it) { oppgaveKø ->
            checkNotNull(oppgaveKø) { "Fant ikke kø ved køoppdatering" }
            if (oppgaveKø.oppgaverOgDatoer == opprinnelige) {
                oppgaveKø.oppgaverOgDatoer = kø.oppgaverOgDatoer
            } else {
                oppgaveKø.oppgaverOgDatoer.clear()
                for (oppgave in aktiveOppgaver) {
                    if (kø.kode6 == oppgave.kode6) {
                        oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                            oppgave = oppgave,
                            erOppgavenReservertSjekk = {false}, //har spesifikt hentet ureserverte oppgaver over
                        )
                    }
                }
            }
            val neste20oppgaveIder = oppgaveKø.oppgaverOgDatoer.take(20).map { it.id }
            k9sakRefreshBehanderListe.addAll(finnK9sakBehandlingIder(neste20oppgaveIder, aktiveOppgaver, oppgaveRepository, log))
            oppgaveKø
        }
        taTiden(log, "refresh antall oppgaver med reserverte og uten reserverte") { oppgaveTjeneste.refreshAntallOppgaverForKø(kø) }

        val behandlingerUuiderTilRefresh = k9sakRefreshBehanderListe.map { it.behandlingUuid }.toSet()
        log.info("Sender ${behandlingerUuiderTilRefresh.size} videre for refresh")
        for (uuid in behandlingerUuiderTilRefresh) {
            refreshOppgaveChannel.send(uuid)
        }
        log.info("Antall oppgaver i refreshOppgaveChannel er nå ${refreshOppgaveChannel.toList().size}")
        log.info("Ferdig")
    }
}
suspend fun <T> taTiden(log : Logger, tekst : String, operasjon: suspend () -> T): T {
    val t0 = System.currentTimeMillis()
    try {
        return operasjon.invoke()
    } finally {
        val t1 = System.currentTimeMillis()
        log.info("Tidsforbruk for $tekst var ${t1-t0} ms")
    }
}

fun finnK9sakBehandlingIder(
    neste20oppgaveIder: List<UUID>,
    innlastedeOppgaver: List<Oppgave>,
    oppgaveRepository: OppgaveRepository,
    log: Logger
): List<BehandlingIdDto> {
    val resultat: MutableList<BehandlingIdDto> = ArrayList()
    val sjekkesMotDb: MutableList<UUID> = mutableListOf()
    for (uuid in neste20oppgaveIder) {
        val innlastetOppgave = innlastedeOppgaver.firstOrNull { it.eksternId == uuid }
        if (innlastetOppgave != null) {
            if (innlastetOppgave.system == Fagsystem.K9SAK.kode) {
                resultat.add(BehandlingIdDto(uuid))
            }
        } else {
            sjekkesMotDb.add(uuid)
        }
    }
    if (sjekkesMotDb.isNotEmpty()) {
        resultat.addAll( oppgaveRepository.hentOppgaver(sjekkesMotDb)
            .filter { it.system == Fagsystem.K9SAK.kode }.map { BehandlingIdDto(it.eksternId) }
        )
    }
    log.info("matcher ${neste20oppgaveIder} oppgaver, hentet ${sjekkesMotDb.size} av disse fra databasen")
    return resultat
}

