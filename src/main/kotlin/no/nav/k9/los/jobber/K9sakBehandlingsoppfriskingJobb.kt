package no.nav.k9.los.jobber

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer

class K9sakBehandlingsoppfriskingJobb(
    val tidMellomRefreshProd : Duration = Duration.ofHours(12),
    val tidMellomRefreshDev : Duration = Duration.ofMinutes(15),
    val startidApplikasjon : LocalDateTime = LocalDateTime.now(),
    val ønsketStarttidJobb : LocalDateTime = avrundFremoverTilKvarter(startidApplikasjon).plusMinutes(1), //1 minutt over neste kvarter
    val tidMellomKjøring: Duration = Duration.ofMinutes(15),
    val forsinketOppstart: Duration = Duration.between(startidApplikasjon, ønsketStarttidJobb),
    val oppgaveKøRepository: OppgaveKøRepository,
    val oppgaveRepository: OppgaveRepository,
    val reservasjonRepository: ReservasjonRepository,
    val refreshOppgaveChannel: Channel<UUID>,
    val antallFraHverKø: Int = 10,
    val configuration: Configuration,
    var sisteRefresh : LocalDateTime? = null
) {
    private val TRÅDNAVN = "k9los-refresh-k9sak-behandlinger-jobb"
    private val log = LoggerFactory.getLogger(K9sakBehandlingsoppfriskingJobb::class.java)

    fun start(): Timer {
        return fixedRateTimer(
            daemon = true,
            name = TRÅDNAVN,
            period = tidMellomKjøring.toMillis(),
            initialDelay = forsinketOppstart.toMillis()
        ) {
            try {
                utfør()
            } catch (e : Exception){
                log.error("Behandlingsoppfriskingsjobb feilet, får ikke oppfrisket behandlinger i k9-sak", e)
            }
        }
    }

    private fun utfør() {
        val nå = LocalDateTime.now()
        val tidSidenSistRefresh = if (sisteRefresh != null) Duration.between(sisteRefresh, nå) else null
        val opphold = if (configuration.koinProfile == KoinProfile.PROD) tidMellomRefreshProd else tidMellomRefreshDev
        val oppholdMedSlack = opphold.minusMinutes(1) //tillater litt slack i tilfelle jobben ikke kjører eksakt på tiden
        if (nå.dayOfWeek == DayOfWeek.SATURDAY || nå.dayOfWeek == DayOfWeek.SUNDAY) {
            log.info("Refresher ikke proaktivt i helg")
        } else if (nå.hour < 6 || nå.hour > 17) {
            //kjører refresh oftere i dev for enklere test
            log.info("Refresher ikke proaktivt utenfor klokken 06-18")
        } else if (tidSidenSistRefresh != null && tidSidenSistRefresh < oppholdMedSlack){
            log.info("Refresher ikke proaktivt, siden det bare har gått ${tidSidenSistRefresh} siden forrige oppdatering, venter til det har gått $opphold")
        } else {
            sisteRefresh = nå
            log.info("Starter refresh av k9sak-behandlinger")
            refresh();
        }
    }

    fun refresh() {
        val behandlingerTilRefresh = finnK9sakBehandlingerTilRefresh()
        channelSend(behandlingerTilRefresh)
    }

    private fun channelSend(behandlingerTilRefresh: Set<UUID>) {
        runBlocking {
            for (uuid in behandlingerTilRefresh) {
                refreshOppgaveChannel.send(uuid)
            }
        }
    }

    fun finnK9sakBehandlingerTilRefresh(): Set<UUID> {
        val k9sakOppgaver = taTiden("hent k9sak oppgaver ") { oppgaveRepository.hentAktiveK9sakOppgaver().toSet() }
        val oppgaverFørstIKøene = taTiden("hent oppgaver først i køene") { hentOppgaverFørstIKøene(k9sakOppgaver) }
        val reserverteOppgaver = taTiden("hent reserverte oppgaver") { hentReserverteOppgaver(k9sakOppgaver) }
        log.info("Refresher ${oppgaverFørstIKøene.size} oppgaver da de er først i køer, og ${reserverteOppgaver.size} reserverte oppgaver")
        return oppgaverFørstIKøene + reserverteOppgaver
    }

    fun hentOppgaverFørstIKøene(k9sakOppgaver: Set<UUID>): Set<UUID> {
        val køene = oppgaveKøRepository.hentIkkeTaHensyn()
        log.info("Hentet ${køene.size} køer")
        return køene.flatMap { kø ->
            kø.oppgaverOgDatoer
                .sortedBy { it.dato }
                .map { it.id }
                .filter { k9sakOppgaver.contains(it) }
                .take(antallFraHverKø)
        }
            .toSet()
    }

    fun hentReserverteOppgaver(k9sakOppgaver: Set<UUID>): Set<UUID> {
        val oppgaveIder = reservasjonRepository.hentOppgaverIdForAlleAktiveReservasjoner()
        return oppgaveIder.filter { k9sakOppgaver.contains(it) }.toSet()
    }

    fun <T> taTiden(tekst: String, operasjon: () -> T): T {
        val t0 = System.currentTimeMillis()
        try {
            return operasjon.invoke()
        } finally {
            val t1 = System.currentTimeMillis()
            log.info("Tidsforbruk for $tekst var ${t1 - t0} ms")
        }
    }

    companion object {
        fun avrundFremoverTilKvarter(tid: LocalDateTime): LocalDateTime {
            return tid.withMinute(0).withSecond(0).withNano(0)
                .plusMinutes(15 * (tid.minute.toLong() / 15))
                .plusMinutes(15)
        }
    }

}