package no.nav.k9.los.nyoppgavestyring.forvaltning

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotliquery.queryOf
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import org.koin.ktor.ext.inject
import java.util.*


fun Route.forvaltningApis() {
    val k9sakEventRepository by inject<K9SakEventRepository>()
    val k9tilbakeEventRepository by inject<K9TilbakeEventRepository>()
    val k9klageEventRepository by inject<K9KlageEventRepository>()
    val k9PunsjEventK9Repository by inject<K9PunsjEventRepository>()
    val oppgaveRepositoryTxWrapper by inject<OppgaveRepositoryTxWrapper>()
    val oppgaveTypeRepository by inject<OppgavetypeRepository>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val oppgaveQueryService by inject<OppgaveQueryService>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()
    val k9TilbakeTilLosHistorikkvaskTjeneste by inject<K9TilbakeTilLosHistorikkvaskTjeneste>()
    val k9KlageTilLosHistorikkvaskTjeneste by inject<K9KlageTilLosHistorikkvaskTjeneste>()
    val k9PunsjTilLosHistorikkvaskTjeneste by inject<K9PunsjTilLosHistorikkvaskTjeneste>()
    val reservasjonV3Repository by inject<ReservasjonV3Repository>()
    val objectMapper = LosObjectMapper.prettyInstance
    val transactionalManager by inject<TransactionalManager>()
    val forvaltningRepository by inject<ForvaltningRepository>()

    val oppgaveKoRepository by inject<OppgaveKøRepository>()
    val oppgaveRepositoryV1 by inject<OppgaveRepository>()

    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()


    get("/index_oversikt", {
        description = "index_oversikt"
        response {
            HttpStatusCode.OK to {
                description = "test test test"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val list = mutableListOf<String>()
                transactionalManager.transaction { tx ->
                    tx.run(
                        queryOf(
                            """
                    SELECT
                    relname AS table_name,
                    indexrelname AS index_name,
                    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
                    idx_scan AS index_scan_count
                    FROM
                    pg_stat_user_indexes
                    ORDER BY
                    index_scan_count ASC,
                    pg_relation_size(indexrelid) DESC;
                """.trimIndent()
                        ).map { row ->
                            if (list.isEmpty()) {
                                list.add(
                                    buildString {
                                        append("${row.underlying.metaData.getColumnLabel(1)} ,")
                                        append("${row.underlying.metaData.getColumnLabel(2)} ,")
                                        append("${row.underlying.metaData.getColumnLabel(3)} ,")
                                        append(row.underlying.metaData.getColumnLabel(4))
                                    }
                                )
                            }
                            list.add(
                                buildString {
                                    append("${row.string(1)} ,")
                                    append("${row.string(2)} ,")
                                    append("${row.string(3)} ,")
                                    append(row.string(4))
                                }
                            )
                        }.asList
                    )
                }
                call.respond(list)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/sammenlignkoer", {
        description = "Sammenlign en V1-kø med en V3 kø, og lever de oppgavene som ikke finnes i begge køer"
        request {
            queryParameter<String>("v1KoId") {
                description = "Id på V1 kø"
                example("07081bc9-5941-408c-95d8-ded6a4ae3b02") {
                    value = "07081bc9-5941-408c-95d8-ded6a4ae3b02"
                }
            }
            queryParameter<Long>("v3KoId") {
                description = "Id på V3 kø"
                example("5") {
                    value = "5"
                }
            }
            queryParameter<String>("skjermet") {
                description = "Vise køer med skjerming"
                example("false") {
                    value = "false"
                }
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val v1KoId = UUID.fromString(call.parameters["v1KoId"])
                val v3KoId = call.parameters["v3KoId"]!!.toLong()
                val skjermet = call.parameters["skjermet"].toBoolean()

                val v3Ko = oppgaveKoTjeneste.hent(v3KoId, skjermet)
                val v3Oppgaver =
                    oppgaveQueryService.queryForOppgaveEksternId(
                        QueryRequest(
                            v3Ko.oppgaveQuery,
                            fjernReserverte = true
                        )
                    ).map { UUID.fromString(it.eksternId) }

                val v1Ko = oppgaveKoRepository.hentOppgavekø(v1KoId, ignorerSkjerming = skjermet)
                val v1Oppgaver = v1Ko.oppgaverOgDatoer.map { it.id }.toList()

                val v3MenIkkeV1 = v3Oppgaver.subtract(v1Oppgaver)
                val v1MenIkkeV3 = v1Oppgaver.subtract(v3Oppgaver)

                val v3OppgaverSomManglerIV1 = v3MenIkkeV1.map {
                    OppgaveIkkeSensitiv(oppgaveRepositoryTxWrapper.hentOppgave("K9", it.toString()))
                }.toList()

                val v1OppgaverSomManglerIV3 = v1MenIkkeV3.map {
                    oppgaveRepositoryV1.hent(it)
                }

                call.respond(KoDiff(v3MenIkkeV1.size, v1MenIkkeV3.size, v3OppgaverSomManglerIV1.toSet(), v1OppgaverSomManglerIV3.toSet()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/eventer/{system}/{eksternId}", {
        description = "Hent ut eventhistorikk for en oppgave"
        request {
            pathParameter<String>("system") {
                description = "Kildesystem som har levert eventene"
                example("k9sak") {
                    value = "K9SAK"
                    description = "Oppgaver fra k9sak"
                }
                example("k9klage") {
                    value = "K9KLAGE"
                    description = "Oppgaver fra k9klage"
                }
            }
            pathParameter<String>("eksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val fagsystem = Fagsystem.fraKode(call.parameters["system"]!!)
                val eksternId = call.parameters["eksternId"]
                when (fagsystem) {
                    Fagsystem.K9SAK -> {
                        val k9SakModell = k9sakEventRepository.hent(UUID.fromString(eksternId))
                        if (k9SakModell.eventer.isEmpty()) {
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            val eventerIkkeSensitive =
                                k9SakModell.eventer.map { event -> K9SakEventIkkeSensitiv(event) }
                            call.respond(objectMapper.writeValueAsString(eventerIkkeSensitive))
                        }
                    }

                    Fagsystem.K9TILBAKE -> {
                        val k9TilbakeModell = k9tilbakeEventRepository.hent(UUID.fromString(eksternId))
                        val eventerIkkeSensitive =
                            k9TilbakeModell.eventer.map { event -> K9TilbakeEventIkkeSensitiv(event) }
                        call.respond(objectMapper.writeValueAsString(eventerIkkeSensitive))
                    }

                    Fagsystem.K9KLAGE -> {
                        val k9KlageModell = k9klageEventRepository.hent(UUID.fromString(eksternId))
                        val eventerIkkeSensitive =
                            k9KlageModell.eventer.map { event -> K9KlageEventIkkeSensitiv(event) }
                        call.respond(objectMapper.writeValueAsString(eventerIkkeSensitive))
                    }

                    Fagsystem.PUNSJ -> {
                        val k9PunsjModell = k9PunsjEventK9Repository.hent(UUID.fromString(eksternId))
                        val eventerIkkeSensitive =
                            k9PunsjModell.eventer.map { event -> K9PunsjEventIkkeSensitiv(event) }
                        call.respond(objectMapper.writeValueAsString(eventerIkkeSensitive))
                    }
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/aktiv", {
        description = "Hent ut nåtilstand for en oppgave"
        request {
            pathParameter<String>("omrade") {
                description = "Området oppgavetypen er definert i. Pr i dag er kun K9 implementert"
                example("K9") {
                    value = "K9"
                    description = "Oppgaver definert innenfor K9"
                }
            }
            pathParameter<String>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = "k9sak"
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val område = call.parameters["omrade"]!!
                val oppgavetype = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                try {
                    val oppgave =
                        oppgaveRepositoryTxWrapper.hentOppgave(område, oppgaveEksternId)
                    call.respond(objectMapper.writeValueAsString(OppgaveIkkeSensitiv(oppgave)))
                } catch (e: IllegalStateException) {
                    if (e.message != null && e.message!!.startsWith("")) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        throw e
                    }
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}", {
        description = "Hent ut oppgavehistorikk for en oppgave"
        request {
            pathParameter<String>("omrade") {
                description = "Området oppgavetypen er definert i. Pr i dag er kun K9 implementert"
                example("K9") {
                    value = "K9"
                    description = "Oppgaver definert innenfor K9"
                }
            }
            pathParameter<String>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = "k9sak"
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val område = call.parameters["omrade"]!!
                val oppgavetype = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                val oppgaveTidsserie =
                    transactionalManager.transaction { tx ->
                        forvaltningRepository.hentOppgaveTidsserie(
                            områdeEksternId = område,
                            oppgaveTypeEksternId = oppgavetype,
                            oppgaveEksternId = oppgaveEksternId,
                            tx = tx
                        )
                    }
                if (oppgaveTidsserie.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val tidsserieIkkeSensitiv = oppgaveTidsserie.map { oppgave -> OppgaveIkkeSensitiv(oppgave) }
                    call.respond(objectMapper.writeValueAsString(tidsserieIkkeSensitiv))
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/historikkvask", {
        description =
            "Kjøre historikkvask for enkeltsak, for å vaske eksisterende oppgavehistorikk mot korresponderende eventer"
        request {
            pathParameter<String>("omrade") {
                description = "Området oppgavetypen er definert i. Pr i dag er kun K9 implementert"
                example("K9") {
                    value = "K9"
                    description = "Oppgaver definert innenfor K9"
                }
            }
            pathParameter<String>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = "k9sak"
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val område = call.parameters["omrade"]!!
                val oppgavetype = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                when (område) {
                    "K9" -> {
                        when (oppgavetype) {
                            "k9sak" -> {
                                k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(
                                    UUID.fromString(
                                        oppgaveEksternId
                                    )
                                )
                                call.respond(HttpStatusCode.NoContent)
                            }

                            "k9tilbake" -> {
                                k9KlageTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(
                                    UUID.fromString(
                                        oppgaveEksternId
                                    )
                                )
                                call.respond(HttpStatusCode.NoContent)
                            }

                            "k9klage" -> {
                                k9TilbakeTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(
                                    UUID.fromString(
                                        oppgaveEksternId
                                    )
                                )
                                call.respond(HttpStatusCode.NoContent)
                            }

                            "k9punsj" -> {
                                k9PunsjTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(
                                    UUID.fromString(
                                        oppgaveEksternId
                                    )
                                )
                                call.respond(HttpStatusCode.NoContent)
                            }

                            else -> call.respond(
                                HttpStatusCode.NotImplemented,
                                "Støtter ikke historikkvask på oppgavetype: $oppgavetype for område: $område"
                            )
                        }
                    }

                    else -> call.respond(HttpStatusCode.NotImplemented, "Støtter ikke historikkvask på område: $område")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/settdirty", {
        description =
            "Sett dirtyflagg på eventhistorikk for å trigge innlesning av eventer som mangler i oppgavehistorikken"
        request {
            pathParameter<String>("omrade") {
                description = "Området oppgavetypen er definert i. Pr i dag er kun K9 implementert"
                example("K9") {
                    value = "K9"
                    description = "Oppgaver definert innenfor K9"
                }
            }
            pathParameter<String>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = "k9sak"
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val område = call.parameters["omrade"]!!
                val oppgavetype = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                when (område) {
                    "K9" -> {
                        when (oppgavetype) {
                            "k9sak" -> {
                                transactionalManager.transaction { tx ->
                                    k9sakEventRepository.settDirty(UUID.fromString(oppgaveEksternId), tx)
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }

                            "k9klage" -> {
                                transactionalManager.transaction { tx ->
                                    k9klageEventRepository.settDirty(UUID.fromString(oppgaveEksternId), tx)
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }

                            "k9tilbake" -> {
                                transactionalManager.transaction { tx ->
                                    k9tilbakeEventRepository.settDirty(UUID.fromString(oppgaveEksternId), tx)
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }

                            "k9punsj" -> {
                                transactionalManager.transaction { tx ->
                                    k9PunsjEventK9Repository.settDirty(UUID.fromString(oppgaveEksternId), tx)
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }

                            else -> call.respond(
                                HttpStatusCode.NotImplemented,
                                "Oppgavetype $oppgavetype for område: $område ikke implementert"
                            )
                        }
                    }

                    else -> call.respond(HttpStatusCode.NotImplemented, "Område: $område ikke implementert")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/reservasjoner", {
        description = "Hent ut reservasjonshistorikk for en oppgave"
        request {
            pathParameter<String>("omrade") {
                description = "Området oppgavetypen er definert i. Pr i dag er kun K9 implementert"
                example("K9") {
                    value = "K9"
                    description = "Oppgaver definert innenfor K9"
                }
            }
            pathParameter<String>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = "k9sak"
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val område = call.parameters["omrade"]!!
                val oppgavetypeEksternId = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                try {
                    oppgaveTypeRepository.hentOppgavetype(område, oppgavetypeEksternId)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message.toString())
                    return@withRequestContext
                }

                val oppgave = oppgaveRepositoryTxWrapper.hentOppgave(område, oppgaveEksternId)
                val reservasjonsnøkkel = utledReservasjonsnøkkel(oppgave, false)
                val reservasjonsnøkkel_beslutter = utledReservasjonsnøkkel(oppgave, true)
                val reservasjonerOrdinær = transactionalManager.transaction { tx ->
                    reservasjonV3Repository.hentReservasjonTidslinjeMedEndringer(reservasjonsnøkkel, tx)
                }
                val reservasjonerBeslutter = transactionalManager.transaction { tx ->
                    reservasjonV3Repository.hentReservasjonTidslinjeMedEndringer(reservasjonsnøkkel_beslutter, tx)
                }

                val reservasjonerSamlet =
                    (reservasjonerOrdinær + reservasjonerBeslutter).sortedBy { it.reservasjonOpprettet }
                call.respond(objectMapper.writeValueAsString(reservasjonerSamlet))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    route("/ytelse") {
        get("/oppgaveko/antall") {
            requestContextService.withRequestContext(call) {
                if (pepClient.kanLeggeUtDriftsmelding()) {
                    val antall = oppgaveKoTjeneste.hentOppgavekøer(skjermet = false).map {
                        oppgaveKoTjeneste.hentAntallOppgaverForKø(
                            oppgaveKoId = it.id,
                            filtrerReserverte = false,
                            skjermet = false
                        )
                    }.size
                    call.respond(antall)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get("/oppgaveko") {
            requestContextService.withRequestContext(call) {
                if (pepClient.kanLeggeUtDriftsmelding()) {
                    call.respond(oppgaveKoTjeneste.hentOppgavekøer(skjermet = false).map { it.id })
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get("/oppgaveko/{ko}/antall") {
            requestContextService.withRequestContext(call) {
                if (pepClient.kanLeggeUtDriftsmelding()) {
                    val køId = call.parameters["ko"]!!.toLong()
                    val medReserverte = call.request.queryParameters["reserverte"]?.toBoolean() ?: false
                    val antall = oppgaveKoTjeneste.hentAntallOppgaverForKø(
                        oppgaveKoId = køId,
                        filtrerReserverte = medReserverte,
                        skjermet = false
                    )
                    call.respond(if (antall > 10) antall else -1)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }

}

fun utledReservasjonsnøkkel(oppgave: Oppgave, erTilBeslutter: Boolean): String {
    return when (FagsakYtelseType.fraKode(oppgave.hentVerdi("ytelsestype"))) {
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.PLEIEPENGER_NÆRSTÅENDE,
        FagsakYtelseType.OMSORGSPENGER_KS,
        FagsakYtelseType.OMSORGSPENGER_AO,
        FagsakYtelseType.OPPLÆRINGSPENGER -> lagNøkkelPleietrengendeAktør(oppgave, erTilBeslutter)

        else -> lagNøkkelAktør(oppgave, erTilBeslutter)
    }
}

fun lagNøkkelPleietrengendeAktør(oppgave: Oppgave, tilBeslutter: Boolean): String {
    return if (tilBeslutter)
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("pleietrengendeAktorId")}_beslutter"
    else {
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("pleietrengendeAktorId")}"
    }
}

fun lagNøkkelAktør(oppgave: Oppgave, tilBeslutter: Boolean): String {
    return if (tilBeslutter) {
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("aktorId")}_beslutter"
    } else {
        "K9_b_${oppgave.hentVerdi("ytelsestype")}_${oppgave.hentVerdi("aktorId")}"
    }
}

data class KoDiff(
    val antallOppgaverSomManglerIV1: Int,
    val antallOppgaverSomManglerIV3: Int,
    val v3OppgaverSomManglerIV1: Set<OppgaveIkkeSensitiv>,
    val v1OppgaverSomManglerIV3: Set<no.nav.k9.los.domene.lager.oppgave.Oppgave>
)
