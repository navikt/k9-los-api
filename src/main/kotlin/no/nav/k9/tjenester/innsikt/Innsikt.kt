package no.nav.k9.tjenester.innsikt

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.locations.*
import io.ktor.routing.*
import kotlinx.html.*
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.repository.*
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.EnheterSomSkalUtelatesFraLos
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Route.innsiktGrensesnitt() {
    val oppgaveRepository by inject<OppgaveRepository>()
    val oppgaveRepositoryV2 by inject<OppgaveRepositoryV2>()
    val statistikkRepository by inject<StatistikkRepository>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val behandlingProsessEventK9Repository by inject<BehandlingProsessEventK9Repository>()

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

    fun BODY.oppgaveSeksjon(saksnummer: String) {
        val oppgaveMedId = oppgaveRepository.hentOppgaverSomMatcherSaksnummer(saksnummer)

        if (oppgaveMedId.isNotEmpty()) {
            val sortedByDescending = oppgaveMedId.sortedByDescending { it.oppgave.eventTid }

            sortedByDescending.forEach { oppgaveMedId1 ->
                val sakModell = behandlingProsessEventK9Repository.hent(oppgaveMedId1.id)

                div {
                    classes = setOf("input-group-text display-4")
                    +"TilBeslutter = ${sakModell.oppgave().tilBeslutter}, saksnummer=$saksnummer"
                }

                sakModell.eventer.forEach { behandlingProsessEventDto ->
                    val stringBuilder = StringBuilder()

                    behandlingProsessEventDto.aksjonspunktKoderMedStatusListe.map { "kode=${it.key}, verdi=${it.value} " }
                        .forEach { stringBuilder.append(it) }

                    div {
                        classes = setOf("input-group-text display-4")
                        +"BId=${oppgaveMedId1.oppgave.eksternId} EventTid=${behandlingProsessEventDto.eventTid}, Aksjonspunkter=$stringBuilder"
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

            val k = oppgaveKøRepository.hentIkkeTaHensyn()
            for (b in k.filter { !it.kode6 }) {
                b.oppgaverOgDatoer.clear()
                for (oppgave in hentAktiveOppgaver) {
                    b.leggOppgaveTilEllerFjernFraKø(oppgave)
                }
            }
            køer = k
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
