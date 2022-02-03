package no.nav.k9.tjenester.mock

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import no.nav.k9.KoinProfile
import no.nav.k9.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.domene.modell.AksjonspunktDefWrapper
import no.nav.k9.domene.modell.AksjonspunktStatus
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.set

fun Route.MockGrensesnitt() {
    val k9sakEventHandler by inject<K9sakEventHandler>()
    val k9punsjEventHandler by inject<K9punsjEventHandler>()
    val behandlingProsessEventRepository by inject<BehandlingProsessEventK9Repository>()
    val punsjEventK9Repository by inject<PunsjEventK9Repository>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val oppgaveRepository by inject<OppgaveRepository>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val profile by inject<KoinProfile>()

    class main

    get { _: main ->
        if (profile == KoinProfile.PROD) {
            call.respond(HttpStatusCode.NotFound)
        }

        call.respondHtml {

            head {
                title { +"Test app for k9-los" }
                styleLink("/static/bootstrap.css")
                script(src = "/static/script.js") {}
            }
            body {
                div {
                    classes = setOf("container ")
                    a {
                        href = "/mock/endreBehandling"
                        +"Endre behandling"
                    }
                    h1 { +"Testside for k9-los" }
                    p {
                        +"Aksjonspunkter toggles av og på som hendelser. En behandling regnes som avsluttet dersom den er opprettet og ikke lengre har noen aksjonspunkter som operative"
                    }


                    div {
                        classes = setOf("input-group", "mb-3")
                        div {
                            classes = setOf("input-group-prepend")
                            span {
                                classes = setOf("input-group-text")
                                +"EksternId"
                            }
                        }
                        textInput {
                            classes = setOf("form-control")
                            id = "uuid"
                            value = UUID.randomUUID().toString()
                        }
                    }

                    div {
                        classes = setOf("input-group", "mb-3")
                        div {
                            classes = setOf("input-group-prepend")
                            span {
                                classes = setOf("input-group-text")
                                +"Aktørid"
                            }
                        }
                        textInput {
                            classes = setOf("form-control")
                            id = "aktørid"
                            value = "26104500284"
                        }
                    }

                    for (aksjonspunkt in Aksjonspunkter().aksjonspunkter()) {
                        div {
                            classes = setOf("input-group")
                            div {
                                classes = setOf("input-group-prepend")
                                div {
                                    classes = setOf("input-group-text")
                                    input(InputType.checkBox) {
                                        id = "Checkbox${aksjonspunkt.kode}"
                                        onClick = "toggle('${aksjonspunkt.kode}')"
                                    }
                                }
                            }
                            div {
                                classes = setOf("input-group-text display-4")
                                +"${aksjonspunkt.kode} ${aksjonspunkt.navn} Type: ${aksjonspunkt.behandlingsstegtype} Plassering: ${aksjonspunkt.plassering} Totrinn: ${aksjonspunkt.totrinn}"
                            }
                        }
                    }
                }
            }
        }
    }

    @Location("/toggleaksjonspunkt")
    class aksjonspunkt
    post { _: aksjonspunkt ->
        if (profile == KoinProfile.PROD) {
            call.respond(HttpStatusCode.NotFound)
        }
        val aksjonspunktToggle = call.receive<AksjonspunktToggle>()

        // punsj time
        if (AksjonspunktDefWrapper.aksjonspunkterFraPunsj().map { a -> a.kode }.contains(aksjonspunktToggle.kode)) {
            val modell = punsjEventK9Repository.hent(UUID.fromString(aksjonspunktToggle.eksternid))

            val punsjEventDto = if (modell.erTom()) {
                PunsjEventDto(
                    eksternId = UUID.fromString(aksjonspunktToggle.eksternid),
                    aksjonspunktKoderMedStatusListe = mutableMapOf(aksjonspunktToggle.kode to AksjonspunktStatus.OPPRETTET.kode),
                    journalpostId = JournalpostId("133742069666"),
                    eventTid = LocalDateTime.now(),
                    aktørId = AktørId(aksjonspunktToggle.aktørid)
                )
            } else {
                val sisteEvent = modell.sisteEvent()
                sisteEvent.aksjonspunktKoderMedStatusListe[aksjonspunktToggle.kode] =
                    if (aksjonspunktToggle.toggle) AksjonspunktStatus.OPPRETTET.kode else AksjonspunktStatus.AVBRUTT.kode

                PunsjEventDto(
                    eksternId = sisteEvent.eksternId,
                    aksjonspunktKoderMedStatusListe = sisteEvent.aksjonspunktKoderMedStatusListe,
                    journalpostId = sisteEvent.journalpostId,
                    eventTid = LocalDateTime.now(),
                    aktørId = sisteEvent.aktørId
                )
            }
            k9punsjEventHandler.prosesser(punsjEventDto)

        } else {
            val modell = behandlingProsessEventRepository.hent(UUID.fromString(aksjonspunktToggle.eksternid))

            val event = if (modell.erTom()) {
                BehandlingProsessEventDto(
                    UUID.fromString(aksjonspunktToggle.eksternid),
                    Fagsystem.K9SAK,
                    "Saksnummer",
                    aksjonspunktToggle.aktørid,
                    1234L,
                    LocalDate.now(),
                    LocalDateTime.now(),
                    EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    behandlingStatus = "UTRED",
                    behandlinStatus = "UTRED",
                    aksjonspunktKoderMedStatusListe = mutableMapOf(aksjonspunktToggle.kode to AksjonspunktStatus.OPPRETTET.kode),
                    behandlingSteg = "",
                    opprettetBehandling = LocalDateTime.now(),
                    behandlingTypeKode = "BT-004",
                    ytelseTypeKode = "OMP"
                )
            } else {
                val sisteEvent = modell.sisteEvent()
                sisteEvent.aksjonspunktKoderMedStatusListe[aksjonspunktToggle.kode] =
                    if (aksjonspunktToggle.toggle) AksjonspunktStatus.OPPRETTET.kode else AksjonspunktStatus.AVBRUTT.kode
                BehandlingProsessEventDto(
                    sisteEvent.eksternId,
                    sisteEvent.fagsystem,
                    sisteEvent.saksnummer,
                    sisteEvent.aktørId,
                    sisteEvent.behandlingId,
                    LocalDate.now(),
                    LocalDateTime.now(),
                    EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    behandlingStatus = sisteEvent.behandlingStatus,
                    behandlinStatus = sisteEvent.behandlinStatus,
                    aksjonspunktKoderMedStatusListe = sisteEvent.aksjonspunktKoderMedStatusListe,
                    behandlingSteg = "",
                    opprettetBehandling = LocalDateTime.now(),
                    behandlingTypeKode = "BT-004",
                    ytelseTypeKode = sisteEvent.ytelseTypeKode
                )
            }
            k9sakEventHandler.prosesser(event)
        }
        call.respond(HttpStatusCode.Accepted)
    }

    @Location("/10000AktiveEventer")
    class aksjonspunkt2

    get { _: aksjonspunkt2 ->
        if (profile == KoinProfile.PROD) {
            call.respond(HttpStatusCode.NotFound)
        }
        for (i in 0..10000 step 1) {

            val event =
                BehandlingProsessEventDto(
                    UUID.randomUUID(),
                    Fagsystem.K9SAK,
                    "Saksnummer",
                    UUID.randomUUID().toString(),
                    1234L,
                    LocalDate.now(),
                    LocalDateTime.now(),
                    EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    behandlingStatus = "UTRED",
                    behandlinStatus = "UTRED",
                    aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjon.AVKLAR_OPPHOLDSRETT.kode to AksjonspunktStatus.OPPRETTET.kode),
                    behandlingSteg = "",
                    opprettetBehandling = LocalDateTime.now(),
                    behandlingTypeKode = "BT-004",
                    ytelseTypeKode = "OMP"
                )

            k9sakEventHandler.prosesser(event)
        }
        call.respond(HttpStatusCode.Accepted)
    }

    @Location("/endreBehandling")
    class endreBehandling

    get { _: endreBehandling ->
        if (profile == KoinProfile.PROD) {
            call.respond(HttpStatusCode.NotFound)
        }
        val valgtKø = call.request.queryParameters["valgtKø"]
        val ferdigStill = call.request.queryParameters["ferdigstill"]
        if (ferdigStill != null) {
            k9sakEventHandler.prosesser(
                behandlingProsessEventRepository.hent(UUID.fromString(ferdigStill)).sisteEvent()
                    .copy(
                        behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
                        aksjonspunktKoderMedStatusListe = mutableMapOf(),
                        eventTid = LocalDateTime.now()
                    )
            )
        }

        val oppgavekøer = oppgaveKøRepository.hentIkkeTaHensyn()

        call.respondHtml {
            head {
                title { +"Test app for k9-los" }
                styleLink("/static/bootstrap.css")
                script(src = "/static/script.js") {}
            }
            body {
                div {
                    classes = setOf("container ")
                    a {
                        href = "/mock"
                        +"Mock"
                    }
                    h1 { +"Endre behandling" }
                    select {
                        classes = setOf("form-control")
                        id = "valgtKø"
                        //language=JavaScript
                        onChange = "window.location.search ='?valgtKø=' + document.getElementById('valgtKø').value;"
                        option {
                            disabled = true
                            selected = true
                            +"Velg kø"
                        }
                        option {
                            selected = "reserverte" == valgtKø
                            value = "reserverte"
                            +"Reserverte"
                        }
                        for (oppgaveKø in oppgavekøer) {
                            option {
                                selected = oppgaveKø.id.toString() == valgtKø
                                value = oppgaveKø.id.toString()
                                +oppgaveKø.navn
                            }
                        }
                    }

                    if (valgtKø != null) {
                        val oppgaver =
                            runBlocking {
                                if (valgtKø == "reserverte") {
                                    oppgaveRepository
                                        .hentOppgaver(
                                            saksbehandlerRepository.hentAlleSaksbehandlereIkkeTaHensyn() .flatMap { it.reservasjoner })
                                } else {
                                    oppgaveRepository
                                        .hentOppgaver(oppgavekøer.first { it.id == UUID.fromString(valgtKø) }
                                            .oppgaverOgDatoer.take(20).map { it.id })
                                }
                            }
                        table {
                            classes = setOf("table")
                            thead {
                                tr {
                                    td {
                                        +"Saksnummer"
                                    }
                                    td {
                                        +"Behandlingstatus"
                                    }
                                    td {
                                        +""
                                    }
                                }
                            }
                            for (oppgave in oppgaver) {
                                tr {
                                    td { +oppgave.fagsakSaksnummer }
                                    td { +oppgave.behandlingStatus.navn }
                                    td {
                                        button {
                                            classes = setOf("btn", "btn-dark")
                                            //language=JavaScript
                                            onClick =
                                                "window.location.search = (window.location.search.lastIndexOf('&') == -1 ? window.location.search : window.location.search.substr(0,window.location.search.lastIndexOf('&'))) +'&ferdigstill=${oppgave.eksternId}';"
                                            +"Ferdigstill"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
