package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.registerrefreshk9sak

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.jobbplanlegger.JobbMetrikker
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
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
    val reservasjonRepository: ReservasjonV3Repository,
    val refreshK9v3Tjeneste: RefreshK9v3Tjeneste,
    val refreshOppgaveChannel: Channel<UUID>,
    val configuration: Configuration,
    val transactionalManager: TransactionalManager,
    val oppgavetypeRepository: OppgavetypeRepository,

    //domenespesifikk konfigurasjon som tilpasses etter erfaringer i prod
    val antallFraHverKø: Int = 10,
    val ignorerReserverteOppgaverSomUtløperEtter : Duration = Duration.ofDays(1),
    val maksAntallReserverteTilRefresh: Int = 250, //midlertidig begrensning til vi får erfaring fra prod

    //state
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
        val isProd = configuration.koinProfile == KoinProfile.PROD
        val nå = LocalDateTime.now()
        val tidSidenSistRefresh = if (sisteRefresh != null) Duration.between(sisteRefresh, nå) else null
        val opphold = if (isProd) tidMellomRefreshProd else tidMellomRefreshDev
        val sisteTime = if (isProd) 16 else 20
        val skipRefreshFraKøer = isProd && nå.hour > 5 //unngå full refresh ved redeploy av applikasjon på dagtid
        val oppholdMedSlack = opphold.minusMinutes(1) //tillater litt slack i tilfelle jobben ikke kjører eksakt på tiden
        if (nå.dayOfWeek == DayOfWeek.SUNDAY) {
            log.info("Refresher ikke proaktivt på søndager")
        } else if (nå.hour < 5 || nå.hour > sisteTime) {
            log.info("Refresher ikke proaktivt utenfor klokken 05-$sisteTime")
        } else if (tidSidenSistRefresh != null && tidSidenSistRefresh < oppholdMedSlack){
            log.info("Refresher ikke proaktivt, siden det bare har gått ${tidSidenSistRefresh} siden forrige oppdatering, venter til det har gått $opphold")
        } else {
            sisteRefresh = nå
            log.info("Starter refresh av k9sak-behandlinger")
            JobbMetrikker.time(TRÅDNAVN) {
                refresh(skipRefreshFraKøer = skipRefreshFraKøer)
            }
        }
    }

    private fun refresh(skipRefreshFraKøer: Boolean) {
        val behandlingerTilRefresh = finnK9sakBehandlingerTilRefresh(skipRefreshFraKøer)
        channelSend(behandlingerTilRefresh)
    }

    private fun finnK9sakBehandlingerTilRefresh(skipRefreshFraKøer: Boolean): Set<UUID> {
        val reserverteOppgaver = taTiden("hent reserverte oppgaver") { hentK9sakReserverteBehandlinger() }
        if (skipRefreshFraKøer){
            log.info("Refresher ${reserverteOppgaver.size} reserverte oppgaver")
            return reserverteOppgaver
        } else {
            val oppgaverFørstIKøer = taTiden("hent oppgaver forrest i køer") { refreshK9v3Tjeneste.behandlingerTilOppfriskning(antallFraHverKø) }
            log.info("Refresher ${oppgaverFørstIKøer.size} oppgaver da de er først i køer, og ${reserverteOppgaver.size} reserverte oppgaver")
            return oppgaverFørstIKøer + reserverteOppgaver
        }
    }

    private fun channelSend(behandlingerTilRefresh: Set<UUID>) {
        runBlocking {
            for (uuid in behandlingerTilRefresh) {
                refreshOppgaveChannel.send(uuid)
            }
            log.info("Antall oppgaver i refreshOppgaveChannel er nå ${refreshOppgaveChannel.toList().size}")
        }
    }

    fun hentK9sakReserverteBehandlinger(): Set<UUID> {
        val k9sakOppgavetype = oppgavetypeRepository.hentOppgavetype("k9", "k9sak")
        val oppgaveEksternIder = transactionalManager.transaction { tx ->
            reservasjonRepository.hentAlleOppgaveEksternIderForOppgavetypeMedAktiveReservasjoner(tx, k9sakOppgavetype)
        }.map { UUID.fromString(it) }.toSet()

        if (oppgaveEksternIder.size > maksAntallReserverteTilRefresh){
            log.info("Fant ${oppgaveEksternIder.size} reservasjoner som kandidat for refresh")
            return oppgaveEksternIder.take(maksAntallReserverteTilRefresh).toSet()
        } else {
            return oppgaveEksternIder
        }
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