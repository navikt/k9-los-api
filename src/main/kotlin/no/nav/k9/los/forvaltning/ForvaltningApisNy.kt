package no.nav.k9.los.forvaltning

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotliquery.queryOf
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.k9.avstemming.AvstemmingsTjeneste
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medInnloggetBruker
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.ko.OppgaveKoTjeneste
import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.AktivOppgaveOppslag
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.TemporalOppgaveOppslag
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.reservasjon.ReservasjonV3Repository
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory


fun Route.forvaltningApisNy() {
    val log = LoggerFactory.getLogger("ForvaltningApis")
    val oppgaveOppslagTjeneste by inject<AktivOppgaveOppslag>()
    val oppgaveTypeRepository by inject<OppgavetypeRepository>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val oppgaveQueryService by inject<OppgaveQueryService>()
    val reservasjonV3Repository by inject<ReservasjonV3Repository>()
    val objectMapper = LosObjectMapper.prettyInstance
    val transactionalManager by inject<TransactionalManager>()
    val avstemmingsTjeneste by inject<AvstemmingsTjeneste>()
    val eventRepository by inject<EventRepository>()
    val statistikkRepository by inject<StatistikkRepository>()
    val temporalOppslagTjeneste by inject<TemporalOppgaveOppslag>()
    val forvaltningRepository by inject<ForvaltningRepository>()
    val områdeKoblingRepository by inject<OmrådeKoblingRepository>()

    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()


    get("/index_oversikt", {
        description = "index_oversikt"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "test test test"
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
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

    get("/{system}/{saksnummer}/finnEksternId", {
        description = "Søk opp eksternId for saksnummer eller journalpostId"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<Fagsystem>("system") {
                description = "Kildesystem som har sendt inn oppgaven"
                example("k9sak") {
                    value = Fagsystem.K9SAK
                    description = "K9sak"
                }
                example("k9punsj") {
                    value = Fagsystem.PUNSJ
                    description = "K9punsj"
                }
            }
            pathParameter<String>("saksnummer") {
                description = "Oppgavens saksnummer, evt journalpostId for punsjoppgaver"
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val fagsystem = Fagsystem.fraKode(call.parameters["system"]!!)
                val saksnummer = call.parameters["saksnummer"]!!
                val oppgavetypeKode = K9Oppgavetypenavn.fraFagsystem(fagsystem).kode

                val sokefelt = when (fagsystem) {
                    Fagsystem.K9SAK,
                    Fagsystem.K9TILBAKE,
                    Fagsystem.K9KLAGE -> "saksnummer"

                    Fagsystem.PUNSJ -> "journalpostId"
                }

                val query = QueryRequest(
                    oppgaveQuery = OppgaveQuery(
                        filtere = listOf(
                            FeltverdiOppgavefilter(
                                område = null,
                                kode = "oppgavetype",
                                operator = EksternFeltverdiOperator.EQUALS,
                                verdi = listOf(oppgavetypeKode)
                            ),
                            FeltverdiOppgavefilter(
                                område = Områder.K9,
                                kode = sokefelt,
                                operator = EksternFeltverdiOperator.EQUALS,
                                verdi = listOf(saksnummer)
                            )
                        ),
                        select = listOf(
                            EnkelSelectFelt(
                                område = Områder.K9,
                                kode = "opprettetTidspunkt"
                            )
                        ),
                        order = listOf(
                            EnkelOrderFelt(
                                område = Områder.K9,
                                kode = "opprettetTidspunkt",
                                økende = true
                            )
                        )
                    ),
                    fjernReserverte = false,
                    avgrensning = null
                )

                val eksternIds = oppgaveQueryService.query(query).map { rad ->
                    val eksternOppgaveId = rad.eksternOppgaveId
                        ?: throw IllegalStateException("OppgaveQueryRad mangler eksternOppgaveId")
                    FinnEksternIdResponse(
                        område = eksternOppgaveId.område,
                        eksternId = eksternOppgaveId.eksternId,
                        opprettetTidspunkt = rad.feltverdier
                            .firstOrNull { it.område == Områder.K9 && it.kode == "opprettetTidspunkt" }
                            ?.verdi
                            ?.toString()
                    )
                }
                call.respond(eksternIds)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{oppgavetype}/{oppgaveEksternId}/aktiv", {
        description = "Hent ut nåtilstand for en oppgave"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<K9Oppgavetypenavn>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = K9Oppgavetypenavn.SAK
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val oppgavetype = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                val oppgave =
                    oppgaveOppslagTjeneste.hentAktivOppgave(oppgaveEksternId, oppgavetype)
                call.respond(objectMapper.writeValueAsString(OppgaveIkkeSensitiv(oppgave)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/oppgaveV3/{oppgavetype}/{oppgaveEksternId}", {
        description = "Hent ut oppgavehistorikk for en oppgave"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<K9Oppgavetypenavn>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = K9Oppgavetypenavn.SAK
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val oppgavetypeEksternId = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                val oppgaveTidsserie = temporalOppslagTjeneste.hentTidsserie(
                    oppgavetypeEksternId = oppgavetypeEksternId,
                    oppgaveEksternId = oppgaveEksternId,
                )
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

    get("/oppgaveV3/{oppgavetype}/{oppgaveEksternId}/reservasjoner", {
        description = "Hent ut reservasjonshistorikk for en oppgave"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<K9Oppgavetypenavn>("oppgavetype") {
                description = "Navnet på oppgavetypen."
                example("k9sak") {
                    value = K9Oppgavetypenavn.SAK
                    description = "Oppgaver som kommer fra k9sak"
                }
            }
            pathParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val område = call.område.eksternId
                val oppgavetypeEksternId = call.parameters["oppgavetype"]!!
                val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

                try {
                    oppgaveTypeRepository.hentOppgavetype(område, oppgavetypeEksternId)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message.toString())
                    return@medInnloggetBruker
                }

                val oppgave = oppgaveOppslagTjeneste.hentAktivOppgave(oppgaveEksternId, oppgavetypeEksternId)
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

    get("/avstemming/{fagsystem}", {
        description =
            "Hent ut liste med åpne behandlinger/journalposter i spesifisert fagsystem og kontroller opp mot åpne oppgaver i los. Returnerer en avviksrapport"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<Fagsystem>("fagsystem") {
                description = "Kildesystem som har levert eventene"
            }
        }
    }) {
        /*
        For et gitt fagsystem/oppgavetype
         1. Be om liste med behandlinger (!avsluttet) fra fagsystem (eksternId, saksnummer(/journalpostId?), ventefrist, ytelsestype)
         2. Hent lokal liste åpne oppgaver
         3. Regn ut diff
         */
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
                val avstemmingsrapport = avstemmingsTjeneste.avstem(fagsystem)
                call.respond(objectMapper.writeValueAsString(avstemmingsrapport))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    route("/ytelse") {
        get("/oppgaveko/antall") {
            medInnloggetBruker { bruker ->
                if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
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
            medInnloggetBruker { bruker ->
                if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                    call.respond(oppgaveKoTjeneste.hentOppgavekøer(skjermet = false).map { it.id })
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get("/oppgaveko/{ko}/antall") {
            medInnloggetBruker { bruker ->
                if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
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

    get("/feltdefinisjon/{kode}/bruk", {
        description = "Hent oppgavekøer og lagrede søk som bruker et spesifikt felt som kriterie"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<String>("kode") {
                description = "Feltdefinisjonens kode (eksternId)"
                example("ytelsestype") { value = "ytelsestype" }
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val område = call.område
                val kode = call.parameters["kode"]!!

                val (køer, lagredeSøk) = transactionalManager.transaction { tx ->
                    val alleKøer = forvaltningRepository.hentAlleOppgavekoerMedQuery(tx)
                    val alleLagredeSøk = forvaltningRepository.hentAlleLagredeSøkMedQuery(tx)
                    alleKøer to alleLagredeSøk
                }

                val matchendeKøer = køer
                    .filter { it.oppgaveQuery.inneholderFelt(område, kode) }
                    .map { OppgavekøFeltbrukDto(id = it.id, tittel = it.tittel) }

                val matchendeSøk = lagredeSøk
                    .filter { it.oppgaveQuery.inneholderFelt(område, kode) }
                    .map {
                        LagretSøkFeltbrukDto(
                            id = it.id,
                            tittel = it.tittel,
                            saksbehandlerEpost = it.saksbehandlerEpost
                        )
                    }

                call.respond(FeltbrukDetaljerDto(oppgavekøer = matchendeKøer, lagredeSøk = matchendeSøk))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/feltdefinisjon/bruk", {
        description =
            "Hent oversikt over alle feltdefinisjoner som er brukt som kriterie i oppgavekøer og lagrede søk, med antall for hver"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val (køer, lagredeSøk) = transactionalManager.transaction { tx ->
                    val alleKøer = forvaltningRepository.hentAlleOppgavekoerMedQuery(tx)
                    val alleLagredeSøk = forvaltningRepository.hentAlleLagredeSøkMedQuery(tx)
                    alleKøer to alleLagredeSøk
                }

                val køTelling = mutableMapOf<Feltreferanse, Int>()
                for (kø in køer) {
                    for (ref in kø.oppgaveQuery.hentAlleFeltreferanser()) {
                        køTelling[ref] = (køTelling[ref] ?: 0) + 1
                    }
                }

                val søkTelling = mutableMapOf<Feltreferanse, Int>()
                for (søk in lagredeSøk) {
                    for (ref in søk.oppgaveQuery.hentAlleFeltreferanser()) {
                        søkTelling[ref] = (søkTelling[ref] ?: 0) + 1
                    }
                }

                val alleReferanser = køTelling.keys + søkTelling.keys
                val resultat = alleReferanser.map { ref ->
                    FeltbrukOversiktDto(
                        område = ref.område,
                        kode = ref.kode,
                        antallOppgavekøer = køTelling[ref] ?: 0,
                        antallLagredeSøk = søkTelling[ref] ?: 0
                    )
                }.sortedWith(compareBy({ it.område ?: "" }, { it.kode }))

                call.respond(resultat)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/bestillHistorikkvaskFraQuery/{fagsystem}", {
        description = "Bestill historikkvask for oppgaver truffet av oppgavequery"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<Fagsystem>("fagsystem") {
                description = "Fagsystemet oppgavene tilhører"
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
                val oppgaveQueryFraRequest = call.receive<OppgaveQuery>()
                if (oppgaveQueryFraRequest.select.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "OppgaveQuery.select støttes ikke for bestilling fra query")
                    return@medInnloggetBruker
                }
                val filtereUtenOppgavetype = oppgaveQueryFraRequest.filtere
                    .filterNot { filter ->
                        filter is FeltverdiOppgavefilter && filter.kode == "oppgavetype"
                    }
                val oppgaveQuery = oppgaveQueryFraRequest.copy(
                    filtere = filtereUtenOppgavetype + FeltverdiOppgavefilter(
                        område = null,
                        kode = "oppgavetype",
                        operator = EksternFeltverdiOperator.EQUALS,
                        verdi = listOf(K9Oppgavetypenavn.fraFagsystem(fagsystem).kode)
                    )
                )
                val eksternIder = oppgaveQueryService.queryForOppgaveEksternId(QueryRequest(oppgaveQuery))
                    .map { it.eksternId }
                    .distinct()

                transactionalManager.transaction { tx ->
                    eksternIder.chunked(1000).forEach { chunk ->
                        eventRepository.bestillHistorikkvask(fagsystem, chunk, tx)
                    }
                }

                call.respond(BestillingFraQueryResponse(eksternIder.size))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/bestillDvhSendingFraQuery/{fagsystem}", {
        description = "Bestill DVH-sending for oppgaver truffet av oppgavequery"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
            pathParameter<DvhSendingFagsystem>("fagsystem") {
                description = "Fagsystemet oppgavene skal begrenses til"
                example("k9sak") {
                    value = DvhSendingFagsystem.K9SAK
                    description = "Oppgaver fra k9sak"
                }
                example("k9klage") {
                    value = DvhSendingFagsystem.K9KLAGE
                    description = "Oppgaver fra k9klage"
                }
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val fagsystem = DvhSendingFagsystem.fraKode(call.parameters["fagsystem"]!!)
                val oppgaveQueryFraRequest = call.receive<OppgaveQuery>()
                if (oppgaveQueryFraRequest.select.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "OppgaveQuery.select støttes ikke for bestilling fra query")
                    return@medInnloggetBruker
                }
                val filtereUtenOppgavetype = oppgaveQueryFraRequest.filtere
                    .filterNot { filter ->
                        filter is FeltverdiOppgavefilter && filter.kode == "oppgavetype"
                    }
                val oppgaveQuery = oppgaveQueryFraRequest.copy(
                    filtere = filtereUtenOppgavetype + FeltverdiOppgavefilter(
                        område = null,
                        kode = "oppgavetype",
                        operator = EksternFeltverdiOperator.EQUALS,
                        verdi = listOf(fagsystem.oppgavetypeKode)
                    )
                )
                val eksternIder = oppgaveQueryService.queryForOppgaveEksternId(QueryRequest(oppgaveQuery))
                    .map { it.eksternId }
                    .distinct()

                transactionalManager.transaction { tx ->
                    eksternIder.chunked(1000).forEach { chunk ->
                        statistikkRepository.bestillDvhSendingForEksternIder(chunk, tx)
                    }
                }

                call.respond(BestillingFraQueryResponse(eksternIder.size))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/omrade/kobling/status", {
        description = "Viser om fremmednøklene mot OMRADE er validert og om indeksene på omrade_id er bygget. " +
                "Migrering V1.0_0107 gjør kun metadataoperasjoner; det som krever full tabellgjennomgang utløses her."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                call.respond(områdeKoblingRepository.hentStatus())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/omrade/kobling/valider-fremmednokler", {
        description = "Validerer fremmednøklene mot OMRADE som ennå ikke er validert. " +
                "Tar SHARE UPDATE EXCLUSIVE og blokkerer verken lesing eller skriving, " +
                "men kan ta tid på store tabeller. Idempotent."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val resultat = områdeKoblingRepository.validerAlleFremmednøkler()
                log.info("Validering av områdefremmednøkler fullført: $resultat")
                call.respond(resultat)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/omrade/kobling/opprett-indekser", {
        description = "Bygger indeks på omrade_id for reservasjon_v3, event_nokkel og oppgave_pep_cache " +
                "med CREATE INDEX CONCURRENTLY, som ikke blokkerer skriving. Idempotent."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = "K9" }
            }
        }
    }) {
        medInnloggetBruker { bruker ->
            if (pepClient.kanLeggeUtDriftsmelding(bruker)) {
                val resultat = områdeKoblingRepository.opprettIndekser()
                log.info("Opprettelse av områdeindekser fullført: $resultat")
                call.respond(resultat)
            } else {
                call.respond(HttpStatusCode.Forbidden)
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

data class FinnEksternIdResponse(
    val område: String,
    val eksternId: String,
    val opprettetTidspunkt: String?,
)

data class BestillingFraQueryResponse(
    val antallEksternIder: Int,
)

enum class DvhSendingFagsystem(@JsonValue val oppgavetypeKode: String) {
    K9SAK("k9sak"),
    K9KLAGE("k9klage");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): DvhSendingFagsystem {
            return entries.find { it.oppgavetypeKode == kode }
                ?: throw IllegalStateException("Kjenner ikke igjen DVH-fagsystem=$kode")
        }
    }
}
