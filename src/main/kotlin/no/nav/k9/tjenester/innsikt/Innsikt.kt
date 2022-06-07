package no.nav.k9.tjenester.innsikt

import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import kotlinx.html.BODY
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.styleLink
import kotlinx.html.title
import kotlinx.html.ul
import no.nav.k9.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.PunsjEventK9Repository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.EnheterSomSkalUtelatesFraLos
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

fun Route.innsiktGrensesnitt() {
    val oppgaveRepository by inject<OppgaveRepository>()
    val oppgaveRepositoryV2 by inject<OppgaveRepositoryV2>()
    val statistikkRepository by inject<StatistikkRepository>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val behandlingProsessEventK9Repository by inject<BehandlingProsessEventK9Repository>()
    val behandlingProsessEventTilbakeRepository by inject<BehandlingProsessEventTilbakeRepository>()
    val punsjEventK9Repository by inject<PunsjEventK9Repository>()

    val LOGGER = LoggerFactory.getLogger(StatistikkRepository::class.java)

    class main
    get { _: main ->
        call.respondHtml {
            head {
                title { +"Innsikt i k9-los" }
                styleLink("/static/bootstrap.css")
                script(src = "/static/script.js") {}
            }
            body {
                div {
                    classes = setOf("container ")

                    h1 { +"Innsikt i k9-los" }
                    p {
                        +"Antall åpne oppgaver fordelt på aksjonspunkt."
                    }

                    val avsluttede = oppgaveRepository.hentMedBehandlingsstatus(BehandlingStatus.AVSLUTTET)
                    val inaktiveIkkeAvsluttedeOppgaver = oppgaveRepository.hentInaktiveIkkeAvluttet()

                    /**
                     *  AVSLUTTET("AVSLU", "Avsluttet"),
                    FATTER_VEDTAK("FVED", "Fatter vedtak"),
                    IVERKSETTER_VEDTAK("IVED", "Iverksetter vedtak"),
                    OPPRETTET("OPPRE", "Opprettet"),
                    UTREDES("UTRED", "Utredes");
                     */
                    val fatterVedtakAvsluttet =
                        oppgaveRepository.hentInaktiveIkkeAvluttetMedBehandlingStatus(BehandlingStatus.FATTER_VEDTAK)
                    val iverksetterVedtakAvsluttet =
                        oppgaveRepository.hentInaktiveIkkeAvluttetMedBehandlingStatus(BehandlingStatus.IVERKSETTER_VEDTAK)
                    val opprettetAvsluttet =
                        oppgaveRepository.hentInaktiveIkkeAvluttetMedBehandlingStatus(BehandlingStatus.OPPRETTET)
                    val utredesAvsluttet =
                        oppgaveRepository.hentInaktiveIkkeAvluttetMedBehandlingStatus(BehandlingStatus.UTREDES)
                    val automatiskProsesserteTotalt = oppgaveRepository.hentAutomatiskProsesserteTotalt()
                    val aksjonspunkter = oppgaveRepository.hentAktiveOppgaversAksjonspunktliste()
                    val oppgaverTotaltAktive = oppgaveRepository.hentAktiveOppgaverTotaltIkkeSkjermede()
                    p {
                        +"Det er nå ${aksjonspunkter.sumOf { it.antall }} åpne aksjonspunkter fordelt på $oppgaverTotaltAktive oppgaver, $inaktiveIkkeAvsluttedeOppgaver inaktive med annen status enn avsluttet (fatter vedtak ${fatterVedtakAvsluttet}, iverksetter vedtak ${iverksetterVedtakAvsluttet}, opprettet ${opprettetAvsluttet}, utredes ${utredesAvsluttet}) og $avsluttede med status avsluttet, $automatiskProsesserteTotalt er prosessert automatisk"
                    }
                    p {
                        +"Totalt ${oppgaverTotaltAktive + inaktiveIkkeAvsluttedeOppgaver + avsluttede}"
                    }
                    for (aksjonspunkt in aksjonspunkter.stream().sorted { o1, o2 -> o2.antall.compareTo(o1.antall) }) {
                        if (aksjonspunkt.antall == 0) {
                            continue
                        }
                        div {
                            classes = setOf("input-group-text display-4")
                            +"${aksjonspunkt.antall} kode: ${aksjonspunkt.kode} ${aksjonspunkt.navn} Totrinn: ${aksjonspunkt.totrinn}"
                        }
                    }


                }
            }
        }
    }

    data class InnsiktEvent(val apMap: Map<String, String>, val eventTid: LocalDateTime)

    fun hentEventer(oppgaveMedId1: OppgaveMedId): List<InnsiktEvent> {
        return when(oppgaveMedId1.oppgave.system) {
            Fagsystem.PUNSJ.kode -> punsjEventK9Repository.hent(oppgaveMedId1.id)
                .eventer.map { InnsiktEvent(it.aksjonspunktKoderMedStatusListe, it.eventTid) }
            Fagsystem.K9TILBAKE.kode, Fagsystem.FPTILBAKE.kode -> behandlingProsessEventTilbakeRepository.hent(oppgaveMedId1.id)
                .eventer.map { InnsiktEvent(it.aksjonspunktKoderMedStatusListe, it.eventTid) }
            else -> behandlingProsessEventK9Repository.hent(oppgaveMedId1.id)
                .eventer.map { InnsiktEvent(it.aksjonspunktKoderMedStatusListe, it.eventTid) }
        }
    }


    fun BODY.oppgaveSeksjon(saksnummer: String) {
        val oppgaveMedId = oppgaveRepository.hentOppgaverSomMatcherSaksnummer(saksnummer)

        if (oppgaveMedId.isNotEmpty()) {
            val sortedByDescending = oppgaveMedId.sortedByDescending { it.oppgave.eventTid }

            sortedByDescending.forEach { oppgaveMedId1 ->

                val oppgave = oppgaveMedId1.oppgave
                div {
                    classes = setOf("input-group-text display-4")
                    +"TilBeslutter = ${oppgave.tilBeslutter}, saksnummer=$saksnummer, fagsystem=${oppgave.system}, behandlingType=${oppgave.behandlingType.navn}, behandlingStatus=${oppgave.behandlingStatus.navn}"
                }

                val sakModell = hentEventer(oppgaveMedId1)
                sakModell.forEach { event ->
                    val stringBuilder = StringBuilder()

                    event.apMap.map { "kode=${it.key}, verdi=${it.value} " }
                        .forEach { stringBuilder.append(it) }

                    div {
                        classes = setOf("input-group-text display-4")
                        +"BId=${oppgave.eksternId} EventTid=${event.eventTid}, Aksjonspunkter=$stringBuilder"
                    }
                }
            }
        }
    }

    get("/sak") {
        call.respondHtml {
            val saksnummer = call.request.queryParameters["saksnummer"]?.split(",")
            head {
                title { +(saksnummer?.let {"Innsikt for saksnummer=$saksnummer"}?: "Oppgi saksnummer") }
                styleLink("/static/bootstrap.css")
                script(src = "/static/script.js") {}
            }
            body {
                if (saksnummer.isNullOrEmpty()) div {+"Oppgi saksnummer"}
                else {
                    h2 { +saksnummer.let {"Innsikt for saksnummer=$saksnummer"} }
                    saksnummer.map { oppgaveSeksjon(it) }
                }

            }

        }
    }

    var køer = listOf<OppgaveKø>()

    @Location("/db")
    class db
    get { _: db ->
        if (køer.isEmpty()) {
            val alleReservasjoner =
                saksbehandlerRepository.hentAlleSaksbehandlereIkkeTaHensyn().flatMap { it.reservasjoner }
            val hentAktiveOppgaver =
                oppgaveRepository.hentAktiveOppgaver().filterNot { alleReservasjoner.contains(it.eksternId) }

            val oppgaveKøer = oppgaveKøRepository.hentIkkeTaHensyn()
            for (oppgaveKø in oppgaveKøer.filter { !it.kode6 }) {
                oppgaveKø.oppgaverOgDatoer.clear()
                for (oppgave in hentAktiveOppgaver) {
                    oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                        oppgave,
                        merknader = oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
                    )
                }
            }
            køer = oppgaveKøer
            call.respondHtml { }
        } else {
            call.respondHtml {
                head {
                    title { +"Innsikt i k9-los" }
                    styleLink("/static/bootstrap.css")
                    script(src = "/static/script.js") {}
                }
                body {
                    val list =
                        oppgaveKøRepository.hentIkkeTaHensyn().filter { !it.kode6 }
                    ul {
                        for (l in list) {
                            val oppgaverOgDatoer = køer.first { it.navn == l.navn }.oppgaverOgDatoer
                            val size = oppgaverOgDatoer.size
                            oppgaverOgDatoer.removeAll(l.oppgaverOgDatoer)

                            li {
                                +"${l.navn}: ${l.oppgaverOgDatoer.size} vs $size"
                            }
                        }
                    }

                    ul {
                        for (mutableEntry in Databasekall.map.entries.toList()
                            .sortedByDescending { mutableEntry -> mutableEntry.value.sum() }) {
                            li {
                                +"${mutableEntry.key}: ${mutableEntry.value} "
                            }
                        }
                    }
                    køer = emptyList()
                }
            }
        }
    }

    route("/oppgaver") {
        get ("/ferdigstilt/{behandlendeEnhet}") {
            val behandlendeEnhet = call.parameters["behandlendeEnhet"]
            val ferdigstilteOppgaver = statistikkRepository.hentFerdigstiltOppgavehistorikk(55)
                .filter { EnheterSomSkalUtelatesFraLos.sjekkKanBrukes(it.behandlendeEnhet) }
                .filter { it.behandlendeEnhet == behandlendeEnhet }
            call.respondHtml {
                innsiktHeader("Ferdigstilte oppgaver")
                body {
                    ul {
                        classes = setOf("list-group")
                        ferdigstilteOppgaver.forEach {
                            listeelement("${it.dato}, ${it.fagsystemType}, ${it.fagsakYtelseType}, ${it.behandlingType}")
                            LOGGER.info("${it.dato}: ${it.fagsystemType}, ${it.fagsakYtelseType}, ${it.behandlingType}, ${it.saksbehandler}")
                        }
                    }
                }
            }
        }

        get {
            val eksternId = call.request.queryParameters["eksternid"]
            val behandling = oppgaveRepositoryV2.hentBehandling(eksternId!!)!!

            call.respondHtml {
                innsiktHeader("Alle oppgaver for $eksternId")
                body {
                    div {
                        classes = setOf("jumbotron")
                        h1 { +"Oppgavebehandling" }

                        ul {
                            classes = setOf("list-group")
                            listeelement("Fagsystem: ${behandling.fagsystem} ")
                            listeelement("ytelse: ${behandling.ytelseType} ")
                            listeelement("behandlingType: ${behandling.behandlingType} ")
                            listeelement("ferdigstilt: ${behandling.ferdigstilt} ")
                            listeelement("Aktive oppgaver ${behandling.aktiveOppgaver().size} ")
                            listeelement("Ferdigstilte oppgaver: ${behandling.ferdigstilteOppgaver().size} ")
                        }

                        h2 { +"Oppgaver" }
                        ul {
                            classes = setOf("list-group")
                            behandling.oppgaver().forEach {
                                listeelement("${it.opprettet}, kode: ${it.oppgaveKode}, status: ${it.oppgaveStatus}, erBeslutter: ${it.erBeslutter} ")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.innsiktHeader(tittel: String) = head {
    title { +(tittel) }
    styleLink("/static/bootstrap.css")
    script(src = "/static/script.js") {}
}


fun UL.listeelement(innhold: String) = li {
    classes = setOf("list-group-item")
    +innhold
}
