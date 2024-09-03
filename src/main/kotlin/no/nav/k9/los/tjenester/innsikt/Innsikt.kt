package no.nav.k9.los.tjenester.innsikt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotliquery.queryOf
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3MedOppgaver
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.Reservasjonsnøkkel
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.EnheterSomSkalUtelatesFraLos
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

fun Route.innsiktGrensesnitt() {
    //TODO finn ut hvordan bruke i dev/prod
    val oppgaveRepository by inject<OppgaveRepository>()
    val oppgaveRepositoryV2 by inject<OppgaveRepositoryV2>()
    val oppgavev3Repository by inject<OppgaveV3Repository>()
    val oppgavetypeRepository by inject<OppgavetypeRepository>()
    val statistikkRepository by inject<StatistikkRepository>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val behandlingProsessEventK9Repository by inject<BehandlingProsessEventK9Repository>()
    val behandlingProsessEventTilbakeRepository by inject<BehandlingProsessEventTilbakeRepository>()
    val behandlingProsessEventKlageRepository by inject<BehandlingProsessEventKlageRepository>()
    val punsjEventK9Repository by inject<PunsjEventK9Repository>()
    val reservasjonRepository by inject<ReservasjonRepository>()
    val reservasjonv3Repository by inject<ReservasjonV3Repository>()
    val reservasjonv3Tjeneste by inject<ReservasjonV3Tjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val transactionalManager by inject<TransactionalManager>()
    val pepClient by inject<IPepClient>()

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
        return when (oppgaveMedId1.oppgave.system) {
            Fagsystem.PUNSJ.kode -> punsjEventK9Repository.hent(oppgaveMedId1.id)
                .eventer.map { InnsiktEvent(it.aksjonspunktKoderMedStatusListe, it.eventTid) }

            Fagsystem.K9TILBAKE.kode -> behandlingProsessEventTilbakeRepository.hent(oppgaveMedId1.id)
                .eventer.map { InnsiktEvent(it.aksjonspunktKoderMedStatusListe, it.eventTid) }

            Fagsystem.K9KLAGE.kode -> behandlingProsessEventKlageRepository.hent(oppgaveMedId1.id)
                .eventer.map { InnsiktEvent(it.aksjonspunkttilstander.associate { it.aksjonspunktKode to it.status.kode }, it.eventTid) }

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
                    +"aktiv=${oppgave.aktiv} TilBeslutter=${oppgave.tilBeslutter}, saksnummer=$saksnummer, fagsystem=${oppgave.system}, behandlingType=${oppgave.behandlingType.navn}, behandlingStatus=${oppgave.behandlingStatus.navn}"
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
                title { +(saksnummer?.let { "Innsikt for saksnummer=$saksnummer" } ?: "Oppgi saksnummer") }
                styleLink("/static/bootstrap.css")
                script(src = "/static/script.js") {}
            }
            body {
                if (saksnummer.isNullOrEmpty()) div { +"Oppgi saksnummer" }
                else {
                    h2 { +saksnummer.let { "Innsikt for saksnummer=$saksnummer" } }
                    saksnummer.map { oppgaveSeksjon(it) }
                }

            }

        }
    }

    suspend fun BODY.oppgavekø(køid: String) {
        val kø = oppgaveKøRepository.hentOppgavekø(UUID.fromString(køid), ignorerSkjerming = true)
        if (kø.skjermet || kø.kode6) return

        val oppgaver = oppgaveRepository.hentOppgaver(kø.oppgaverOgDatoer.map { it.id }).sortedBy { it.eventTid }
        div {
            classes = setOf("input-group-text display-4")
            +"kønavn=${kø.navn} antallOppgaverKøTabell=${kø.oppgaverOgDatoer.size} antallOppgaver=${oppgaver.size} ${kø.sortering.kode}"
        }

        oppgaver.forEach { oppgave ->
            div {
                classes = setOf("input-group-text display-4")
                +"sisteEvent=${oppgave.eventTid},opprettet=${oppgave.behandlingOpprettet},BId=${oppgave.eksternId},saksnummer=${oppgave.fagsakSaksnummer},journalpostId=${oppgave.journalpostId},stønadsType=${oppgave.fagsakYtelseType},behandlingsType=${oppgave.behandlingType}"
            }
        }

    }

    get("/oppgaveko") {
        call.respondHtml {
            val køIder = call.request.queryParameters["id"]?.split(",")
            head {
                title { +(køIder?.let { "Innsikt for køid=$køIder" } ?: "Oppgi køid") }
                styleLink("/static/bootstrap.css")
                script(src = "/static/script.js") {}
            }
            body {
                if (køIder.isNullOrEmpty()) div { +"Oppgi køid" }
                else {
                    h2 { +køIder.let { "Innsikt for køid=$køIder" } }
                    runBlocking {
                        køIder.map { køId -> oppgavekø(køId) }
                    }
                }
            }
        }
    }

    var køer = listOf<OppgaveKø>()

    @Location("/db")
    class db
    get { _: db ->
        if (køer.isEmpty()) {
            val hentAktiveOppgaver = oppgaveRepository.hentAktiveUreserverteOppgaver()

            val oppgaveKøer = oppgaveKøRepository.hentIkkeTaHensyn()
            for (oppgaveKø in oppgaveKøer.filterNot { it.kode6 || it.skjermet }) {
                oppgaveKø.oppgaverOgDatoer.clear()
                for (oppgave in hentAktiveOppgaver) {
                    oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                        oppgave,
                        erOppgavenReservertSjekk = {false},
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
                        oppgaveKøRepository.hentIkkeTaHensyn().filterNot { it.kode6 || it.skjermet }
                    ul {
                        for (l in list) {
                            val oppgaverOgDatoer = køer.first { it.navn == l.navn }.oppgaverOgDatoer
                            val size = oppgaverOgDatoer.size
                            oppgaverOgDatoer.removeAll(l.oppgaverOgDatoer)

                            li {
                                +"${l.navn}: ${l.oppgaverOgDatoer.size} vs $size - id=${l.id}"
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


    suspend fun hentReservasjoner(saksnummer: String, oppgaverMedRelasjoner: List<OppgaveMedId>, medHistorikk: Boolean, call: ApplicationCall) {
        if (pepClient.erSakKode7EllerEgenAnsatt(saksnummer)) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        if (oppgaverMedRelasjoner.isEmpty())  {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val reservasjonerV1Aktive = oppgaverMedRelasjoner.mapNotNull { oppgave -> reservasjonRepository.hentOptional(oppgave.id) }
        val reservasjonerV1Historiske = oppgaverMedRelasjoner.flatMap { oppgave -> reservasjonRepository.hentMedHistorikk(oppgave.id) }.toMutableSet().apply { removeAll(reservasjonerV1Aktive) }

        val reservasjonerV3 = transactionalManager.transaction { tx ->
            oppgaverMedRelasjoner.associateWith { (_, oppgave) ->
                listOf(
                    "Pleietrengende" to reservasjonv3Repository.hentAktiveOgHistoriskeReservasjonerForReservasjonsnøkkel("K9_b_${oppgave.fagsakYtelseType.kode}_${oppgave.pleietrengendeAktørId}", tx),
                    "Pleietrengende_Beslutter" to reservasjonv3Repository.hentAktiveOgHistoriskeReservasjonerForReservasjonsnøkkel("K9_b_${oppgave.fagsakYtelseType.kode}_${oppgave.pleietrengendeAktørId}_beslutter", tx),
                    "Aktørid" to reservasjonv3Repository.hentAktiveOgHistoriskeReservasjonerForReservasjonsnøkkel("K9_b_${oppgave.fagsakYtelseType.kode}_${oppgave.aktorId}", tx),
                    "Aktørid_Beslutter" to reservasjonv3Repository.hentAktiveOgHistoriskeReservasjonerForReservasjonsnøkkel("K9_b_${oppgave.fagsakYtelseType.kode}_${oppgave.aktorId}_beslutter", tx),
                    "Legacy" to reservasjonv3Repository.hentAktiveOgHistoriskeReservasjonerForReservasjonsnøkkel("legacy_${oppgave.eksternId}", tx)
                ).filter { it.second.isNotEmpty() }.toMap()
            }
        }

        call.respondHtml {
            innsiktHeader("Reservasjoner for saksnummer $saksnummer")
            body {
                h2 { +"Reservasjoner V1" }
                div { +"Aktive"}
                ul {
                    classes = setOf("list-group")
                    reservasjonerV1Aktive.forEach {
                        val oppgave = oppgaveRepository.hent(it.oppgave)
                        listeelement("oppgaveid: ${it.oppgave}, aktiv: ${it.erAktiv()}, reservertTil: ${it.reservertTil}, reservertAv: ${it.reservertAv} flyttetTidspunkt: ${it.flyttetTidspunkt}, ${if(oppgave.tilBeslutter) { "beslutter" } else "ordinær"}")
                    }
                }

                if (medHistorikk) {
                    div { +"Historiske" }
                    ul {
                        classes = setOf("list-group")
                        reservasjonerV1Historiske.forEach {
                            val oppgave = oppgaveRepository.hent(it.oppgave)
                            listeelement("oppgaveid: ${it.oppgave}, aktiv: ${it.erAktiv()}, reservertTil: ${it.reservertTil}, reservertAv: ${it.reservertAv} flyttetTidspunkt: ${it.flyttetTidspunkt}, ${if(oppgave.tilBeslutter) { "beslutter" } else "ordinær"}")
                        }
                    }
                }


                h2 { +"Reservasjoner V3" }
                reservasjonerV3.forEach { oppgave, reservasjonerOgKilde ->
                    val aktive = reservasjonerOgKilde.mapValues { (_, reservasjoner) -> reservasjoner.filterNot { it.annullertFørUtløp || it.gyldigTil.isBefore(LocalDateTime.now()) } }
                    val historiske = reservasjonerOgKilde.mapValues { (_, reservasjoner) -> reservasjoner.filter { it.annullertFørUtløp || it.gyldigTil.isBefore(LocalDateTime.now()) } }

                    div { +"Reservasjoner for oppgaveid ${oppgave.id}:" }
                    div { +"Aktive"}
                    ul {
                        classes = setOf("list-group")
                        aktive.forEach { (kilde, reservasjoner) ->
                            reservasjoner.forEach { r ->
                            listeelement("""
                                reservasjonsid: ${r.id}, 
                                annullertFørUtløp: ${r.annullertFørUtløp}, 
                                gyldigPeriode: (${r.gyldigFra}-${r.gyldigTil}), 
                                reservertAv: ${r.reservertAv.let { saksbehandlerRepository.finnSaksbehandlerMedId(it)!!.brukerIdent }}, 
                                reservasjonsnøkkelType: $kilde
                            """.trimMargin())
                            }
                        }
                    }
                    if (medHistorikk) {
                        div { +"Historiske"}
                        ul {
                            classes = setOf("list-group")
                            historiske.forEach { (kilde, reservasjoner) ->
                                reservasjoner.forEach { r ->
                                listeelement("""
                                    reservasjonsid: ${r.id}, 
                                    annullertFørUtløp: ${r.annullertFørUtløp}, 
                                    gyldigPeriode: (${r.gyldigFra}-${r.gyldigTil}), 
                                    reservertAv: ${r.reservertAv.let { saksbehandlerRepository.finnSaksbehandlerMedId(it)!!.brukerIdent }}, 
                                    reservasjonsnøkkelType: $kilde
                                """.trimMargin())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    route("/oppgave") {
        get("aktiv/{eksternid}") {
            val eksternId = call.parameters["eksternid"] ?: throw IllegalStateException("eksternid ikke oppgitt")
            val aktivOppgave = transactionalManager.transaction { tx ->
                val oppgavetype = oppgavetypeRepository.hentOppgavetype(
                    område = "K9",
                    eksternId = "k9sak",
                    tx = tx
                )
                oppgavev3Repository.hentAktivOppgave(eksternId, oppgavetype, tx)
            }
            call.respondHtml {
                innsiktHeader("OppgaveV3 for eksternid")
                body {
                    div { +"id: ${aktivOppgave?.id}, status: ${aktivOppgave?.status} endret: ${aktivOppgave?.endretTidspunkt}" }
                }
            }
        }

        get("{eksternid}") {
            val eksternId = call.parameters["eksternid"] ?: throw IllegalStateException("eksternid ikke oppgitt")
            val oppgaver = transactionalManager.transaction { tx ->
                tx.run(
                    queryOf(
                        "select * from oppgave_v3 where ekstern_id = :eksternId", mapOf("eksternId" to eksternId)
                    ).map { row ->
                        Triple(
                            Oppgavestatus.valueOf(row.string("status")),
                            row.string("reservasjonsnokkel"),
                            row.localDateTime("endret_tidspunkt"),
                        ) to row.boolean("aktiv")
                    }.asList
                )
            }
            call.respondHtml {
                innsiktHeader("OppgaveV3 for eksternid")
                body {
                    h2 { +"Aktive" }
                    ul {
                        classes = setOf("list-group")
                        oppgaver.filter { it.second }.forEach { (oppgave, _) ->
                            listeelement("status: ${oppgave.first}, sist_endret: ${oppgave.third}, reservasjonstype: ${oppgave.second.utledStatus()}")
                        }
                    }
                    h2 { +"Historikk" }
                    ul {
                        classes = setOf("list-group")
                        oppgaver.filterNot { it.second }.forEach { (oppgave, _) ->
                            listeelement("status: ${oppgave.first}, sist_endret: ${oppgave.third}, reservasjonstype: ${oppgave.second.utledStatus()}")
                        }
                    }
                }
            }
        }
    }

    route("/reservasjoner") {
        get("/hent/{saksnummer}") {
            val saksnummer = call.parameters["saksnummer"] ?: throw IllegalStateException("Saksnummer ikke oppgitt")
            val medHistorikk = call.request.queryParameters["historikk"]?.let { true } ?: false

            val oppgaver = oppgaveRepository.hentOppgaverSomMatcherSaksnummer(saksnummer).distinct()
            if (oppgaver.size > 1) LOGGER.info("Fant flere enn 1 oppgave på saksnummer $saksnummer")
            val oppgaverMedRelasjoner = oppgaver.flatMap { oppgave -> relaterteOppgaverV1(oppgave, oppgaveRepository) }

            hentReservasjoner(saksnummer, oppgaverMedRelasjoner, medHistorikk, call)
        }

        route("aktive") {
            get("v1") {
                val fagsystem = call.request.queryParameters["fagsystem"]?.let { Fagsystem.fraKode(it) }

                val aktiveV1Reservasjoner = oppgaveRepository.hentAktiveOppgaver()
                    .mapNotNull { oppgaveId -> reservasjonRepository.hentOptional(oppgaveId)?.let { oppgaveId to it } }
                    .filterNot { pepClient.erSakKode7EllerEgenAnsatt(oppgaveRepository.hent(it.second.oppgave).fagsakSaksnummer) }

                call.respondHtml {
                    innsiktHeader("Aktive reservasjoner i v1")
                    body {
                        ul {
                            classes = setOf("list-group")
                            aktiveV1Reservasjoner.forEach { (id, r) ->
                                val oppgave = oppgaveRepository.hent(id)
                                if (fagsystem != Fagsystem.K9SAK || oppgave.harFagSaksNummer()) {
                                    listeelement("saksnummer: ${oppgave.fagsakSaksnummer}, eksternId: $id, aktiv: ${r.erAktiv()}, reservertTil: ${r.reservertTil}, reservertAv: ${r.reservertAv} flyttetTidspunkt: ${r.flyttetTidspunkt}, ${if(oppgave.tilBeslutter) { "beslutter" } else "ordinær"}")
                                }
                            }
                        }
                    }
                }
            }

            get("v3") {
                val fagsystem = call.request.queryParameters["fagsystem"]?.let { Fagsystem.fraKode(it) }

                val aktiveV3Reservasjoner = reservasjonv3Tjeneste.hentAlleAktiveReservasjoner()
                    .filterNot {
                        it.saksnummer().any { pepClient.erSakKode7EllerEgenAnsatt(it) }
                    }

                call.respondHtml {
                    innsiktHeader("Aktive reservasjoner i v3")
                    body {
                        ul {
                            classes = setOf("list-group")
                            transactionalManager.transaction { tx ->
                                aktiveV3Reservasjoner.forEach { r ->
                                    if (fagsystem != Fagsystem.K9SAK || aktiveV3Reservasjoner.any { it.saksnummer().isNotEmpty() }) {
                                    listeelement("""
                                        saksnummer: ${r.saksnummer()}, 
                                        eksternId: ${r.eksternId().joinToString(", ", "[", "]")}, 
                                        reservasjonsid: ${r.reservasjonV3.id}, 
                                        annullertFørUtløp: ${r.reservasjonV3.annullertFørUtløp}, 
                                        gyldigPeriode: (${r.reservasjonV3.gyldigFra}-${r.reservasjonV3.gyldigTil}), 
                                        reservertAv: ${r.reservasjonV3.reservertAv.let { saksbehandlerRepository.finnSaksbehandlerMedId(it)!!.brukerIdent } },
                                        ${r.utledFraReservasjonsnøkkel()}
                                    """.trimMargin())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        get("status") {
            val aktiveV1Reservasjoner = oppgaveRepository.hentAktiveOppgaver()
                .mapNotNull { oppgaveId -> reservasjonRepository.hentOptional(oppgaveId)?.let { it to oppgaveId } }
                .associate { it.second to it.first }

            val aktiveV3Reservasjoner = reservasjonv3Tjeneste.hentAlleAktiveReservasjoner()

            val aktiveV3ReservasjonerForV1Oppgaver = aktiveV3Reservasjoner
                .associateWith { it.eksternId() }

            val aktiveV1nøkler = aktiveV1Reservasjoner.keys
            val resultatv3ReservasjonerSomIkkeFinnesIV1 = aktiveV3ReservasjonerForV1Oppgaver.filter { (_, eksternIder) -> eksternIder.none { aktiveV1nøkler.contains(it) }}

            val aktiveV3Nøkler = aktiveV3ReservasjonerForV1Oppgaver.values.flatten()
            val resultatv1ReservasjonerSomIkkeFinnesIV3 = aktiveV1Reservasjoner.filterNot { aktiveV3Nøkler.contains(it.key) }

            call.respondHtml {
                innsiktHeader("Differanse i reservasjoner v1 til v3")
                body {
                    h2 { +"Reservasjoner v1 som mangler i v3" }
                    ul {
                        classes = setOf("list-group")
                        resultatv1ReservasjonerSomIkkeFinnesIV3.forEach {(id, r) ->
                            val oppgave = oppgaveRepository.hent(id)
                            runBlocking {
                                if (!pepClient.erSakKode7EllerEgenAnsatt(oppgave.fagsakSaksnummer)) {
                                    listeelement("saksnummer: ${oppgave.fagsakSaksnummer}, eksternId: $id, aktiv: ${r.erAktiv()}, reservertTil: ${r.reservertTil}, reservertAv: ${r.reservertAv} flyttetTidspunkt: ${r.flyttetTidspunkt}, ${if(oppgave.tilBeslutter) { "beslutter" } else "ordinær"}")
                                }
                            }
                        }
                    }

                    h2 { +"Reservasjoner i v3 som mangler i v1" }
                    ul {
                        classes = setOf("list-group")
                        resultatv3ReservasjonerSomIkkeFinnesIV1.forEach { (r, ider) ->
                            val saksnummer = r.saksnummer()
                            runBlocking {
                                if (saksnummer.none { pepClient.erSakKode7EllerEgenAnsatt(it) }) {
                                    listeelement("""
                                        saksnummer: [${saksnummer.joinToString(", ")}], 
                                        eksternId: [${r.eksternId()}], 
                                        reservasjonsid: ${r.reservasjonV3.id}, 
                                        annullertFørUtløp: ${r.reservasjonV3.annullertFørUtløp}, 
                                        gyldigPeriode: (${r.reservasjonV3.gyldigFra}-${r.reservasjonV3.gyldigTil}), 
                                        reservertAv: ${r.reservasjonV3.reservertAv.let { saksbehandlerRepository.finnSaksbehandlerMedId(it)!!.brukerIdent } },
                                        ${r.utledFraReservasjonsnøkkel()}
                                        """)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    route("/oppgaver") {
        get("/ferdigstilt/{behandlendeEnhet}") {
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

        fun køDistribusjon(): Map<Int, List<Oppgave>> {
            val aktiveOppgaver = oppgaveRepository.hentAktiveUreserverteOppgaver()
            val oppgavekøer = oppgaveKøRepository.hentIkkeTaHensyn().filter { it.oppgaverOgDatoer.isNotEmpty() }
            return aktiveOppgaver
                .groupBy { oppgave ->
                    oppgavekøer.count { kø ->
                        kø.oppgaverOgDatoer.map { it.id }.contains(oppgave.eksternId)
                    }
                }
        }

        route("/aktive") {
            route("/distribusjon") {
                get {
                    val oppgaverIAntallKøer = køDistribusjon().toSortedMap()
                    call.respondHtml {
                        innsiktHeader("Oppgave funnet i antall køer")
                        body {
                            if (oppgaverIAntallKøer.isNotEmpty()) {
                                ul {
                                    classes = setOf("list-group")
                                    oppgaverIAntallKøer.forEach {
                                        listeelement(
                                            "${it.key} - Antall oppgaver: ${it.value.count()}",
                                            "distribusjon/${it.key}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                get("/{antall}") {
                    val antall = call.parameters["antall"]!!.toInt()
                    val oppgaver = køDistribusjon()[antall] ?: emptyList()

                    call.respondHtml {
                        innsiktHeader("Oppgaver som finnes valgt antall køer")
                        body {
                            div { +"Antall oppgaver i valgt distribusjon: ${oppgaver.size}" }
                            if (oppgaver.isNotEmpty()) {
                                ul {
                                    classes = setOf("list-group")
                                    oppgaver
                                        .filterNot { it.kode6 || it.skjermet }
                                        .forEach {
                                            listeelement(
                                                "${it.eksternId}, Saksnummer: ${it.fagsakSaksnummer}, Beslutter: ${it.tilBeslutter}",
                                                "${it.eksternId}/tilhorer-ko"
                                            )
                                        }
                                }
                            }
                        }
                    }
                }

                get("/{eksternId}/tilhorer-ko") {
                    val eksternId = call.parameters["eksternId"]!!
                    val oppgave = oppgaveRepository.hent(UUID.fromString(eksternId))
                    val oppgavekøer = if (oppgave.kode6 || oppgave.skjermet) {
                        listOf()
                    } else {
                        oppgaveKøRepository.hentIkkeTaHensyn().filterNot { it.kode6 || it.skjermet }
                    }

                    val køerSomInneholderOppgave = oppgavekøer.filter { kø ->
                        kø.oppgaverOgDatoer.map { it.id }.contains(oppgave.eksternId)
                    }

                    call.respondHtml {
                        innsiktHeader("Køer som inneholder oppgave")
                        body {
                            div { +"Antall køer som inneholder oppgaven: ${køerSomInneholderOppgave.size}" }
                            if (køerSomInneholderOppgave.isNotEmpty()) {
                                ul {
                                    classes = setOf("list-group")
                                    køerSomInneholderOppgave.forEach {
                                        listeelement(
                                            "Navn: '${it.navn}', SistEndret: ${it.sistEndret}, Id: ${it.id}, GjelderYtelse: ${
                                                it.filtreringYtelseTyper.joinToString(
                                                    "-"
                                                )
                                            }, AndreKriterier: ${
                                                it.filtreringAndreKriterierType.filter { it.inkluder }.joinToString("-")
                                            }"
                                        )
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

private fun relaterteOppgaverV1(
    oppgave: OppgaveMedId,
    oppgaveRepository: OppgaveRepository
): List<OppgaveMedId> {
    val pleietrengendeAktørId = oppgave.oppgave.pleietrengendeAktørId
    return if (pleietrengendeAktørId != null) {
        oppgaveRepository.hentOppgaverSomMatcher(pleietrengendeAktørId, oppgave.oppgave.fagsakYtelseType)
            .ifEmpty { listOf(oppgave) }
    } else {
        listOf(oppgave)
    }
}

fun HTML.innsiktHeader(tittel: String) = head {
    title { +(tittel) }
    styleLink("/static/bootstrap.css")
    script(src = "/static/script.js") {}
}


fun UL.listeelement(innhold: String, href: String? = null) = li {
    classes = setOf("list-group-item")
    if (href != null) {
        a(href) {
            +innhold
        }
    } else {
        +innhold
    }
}

fun ReservasjonV3MedOppgaver.eksternId(): List<UUID> {
    return oppgaverV3.map { UUID.fromString(it.eksternId) }.takeIf { it.isNotEmpty() } ?: listOfNotNull(oppgaveV1?.eksternId)
}

fun ReservasjonV3MedOppgaver.saksnummer(): List<String> {
    return oppgaverV3.mapNotNull { it.hentVerdi("saksnummer") }.takeIf { it.isNotEmpty() } ?: listOfNotNull(oppgaveV1?.fagsakSaksnummer)
}

fun ReservasjonV3MedOppgaver.utledFraReservasjonsnøkkel(): String {
    return reservasjonV3.reservasjonsnøkkel.utledStatus()
}

fun String.utledStatus(): String {
    return Reservasjonsnøkkel(this).toString()
}