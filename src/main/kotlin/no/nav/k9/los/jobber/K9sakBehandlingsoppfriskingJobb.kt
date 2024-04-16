package no.nav.k9.los.jobber

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.integrasjon.k9.IK9SakService
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer

class K9sakBehandlingsoppfriskingJobb(

    val tidMellomKjøring: Duration = Duration.ofHours(1),
    val ønsketStartid: LocalDateTime = LocalDateTime.now().plusHours(1).withMinute(1).withSecond(0),
    val forsinketOppstart: Duration = Duration.between(LocalDateTime.now(), ønsketStartid),
    val oppgaveKøRepository: OppgaveKøRepository,
    val oppgaveRepository: OppgaveRepository,
    val reservasjonRepository: ReservasjonRepository,
    val k9SakService: IK9SakService,
    val antallFraHverKø: Int = 20,
    val configuration: Configuration,
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
        if (nå.dayOfWeek == DayOfWeek.SATURDAY || nå.dayOfWeek == DayOfWeek.SUNDAY) {
            log.info("Refresher ikke proaktivt i helg")
        } else if (configuration.koinProfile == KoinProfile.PROD && (nå.hour < 6 || nå.hour > 17)){
            //TODO gjør 'cache' i K9SakServiceSystemClient persistent eller gjør andre tiltak for å unngå dobbel-refresh ved restart av applikasjonen
            log.info("Refresher ikke proaktivt utenfor klokken 06-07 for å unngå dobbel-refresh dersom applikasjonen må restartes")
        } else if (nå.hour < 6 || nå.hour > 17) {
            //kjører refresh oftere i dev for enklere test
            log.info("Refresher ikke proaktivt utenfor klokken 06-18")
        } else {
            log.info("Starter refresh av k9sak-behandlinger")
            refresh();
        }
    }

    fun refresh() {
        val behandlingerTilRefresh = finnK9sakBehandlingerTilRefresh()
        runBlocking {
            //TODO send over channel til RefreshK9 i stedet
            k9SakService.refreshBehandlinger(behandlingerTilRefresh)
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

}