package no.nav.k9.los.nyoppgavestyring.forvaltning

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotliquery.queryOf
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.AvstemmingsTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.uthenting.AktivOppgaveOppslag
import no.nav.k9.los.nyoppgavestyring.uthenting.Oppgave
import no.nav.k9.los.nyoppgavestyring.uthenting.TemporalOppgaveOppslag
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory


fun Route.forvaltningApis() {
    val log = LoggerFactory.getLogger("ForvaltningApis")
    val oppgaveOppslagTjeneste by inject<AktivOppgaveOppslag>()
    val oppgaveTypeRepository by inject<OppgavetypeRepository>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val oppgaveQueryService by inject<OppgaveQueryService>()
    val reservasjonV3Repository by inject<ReservasjonV3Repository>()
    val objectMapper = LosObjectMapper.prettyInstance
    val transactionalManager by inject<TransactionalManager>()
    val avstemmingsTjeneste by inject<AvstemmingsTjeneste>()
    val statistikkRepository by inject<StatistikkRepository>()
    val temporalOppslagTjeneste by inject<TemporalOppgaveOppslag>()
    val forvaltningRepository by inject<ForvaltningRepository>()

    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()


    get("/index_oversikt", {
        tags("Forvaltning")
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

    get("/{system}/{saksnummer}/finnEksternId", {
        tags("Forvaltning")
        description = "Søk opp eksternId for saksnummer eller journalpostId"
        request {
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
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
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
                                område = "K9",
                                kode = sokefelt,
                                operator = EksternFeltverdiOperator.EQUALS,
                                verdi = listOf(saksnummer)
                            )
                        ),
                        select = listOf(
                            EnkelSelectFelt(
                                område = "K9",
                                kode = "opprettetTidspunkt"
                            )
                        ),
                        order = listOf(
                            EnkelOrderFelt(
                                område = "K9",
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
                            .firstOrNull { it.område == "K9" && it.kode == "opprettetTidspunkt" }
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
        tags("Forvaltning")
        description = "Hent ut nåtilstand for en oppgave"
        request {
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
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
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
        tags("Forvaltning")
        description = "Hent ut oppgavehistorikk for en oppgave"
        request {
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
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
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

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/reservasjoner", {
        tags("Forvaltning")
        description = "Hent ut reservasjonshistorikk for en oppgave"
        request {
            pathParameter<String>("omrade") {
                description = "Området oppgavetypen er definert i. Pr i dag er kun K9 implementert"
                example("K9") {
                    value = "K9"
                    description = "Oppgaver definert innenfor K9"
                }
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
        tags("Forvaltning")
        description =
            "Hent ut liste med åpne behandlinger/journalposter i spesifisert fagsystem og kontroller opp mot åpne oppgaver i los. Returnerer en avviksrapport"
        request {
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
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
                val avstemmingsrapport = avstemmingsTjeneste.avstem(fagsystem)
                call.respond(objectMapper.writeValueAsString(avstemmingsrapport))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    route("/ytelse") {
        get("/oppgaveko/antall", { tags("Forvaltning") }) {
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

        get("/oppgaveko", { tags("Forvaltning") }) {
            requestContextService.withRequestContext(call) {
                if (pepClient.kanLeggeUtDriftsmelding()) {
                    call.respond(oppgaveKoTjeneste.hentOppgavekøer(skjermet = false).map { it.id })
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get("/oppgaveko/{ko}/antall", { tags("Forvaltning") }) {
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

    get("/feltdefinisjon/{omrade}/{kode}/bruk", {
        tags("Forvaltning")
        description = "Hent oppgavekøer og lagrede søk som bruker et spesifikt felt som kriterie"
        request {
            pathParameter<String>("omrade") {
                description = "Området feltdefinisjonen tilhører"
                example("K9") { value = "K9" }
            }
            pathParameter<String>("kode") {
                description = "Feltdefinisjonens kode (eksternId)"
                example("ytelsestype") { value = "ytelsestype" }
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val område = call.parameters["omrade"].let { if (it == "null" || it == null) null else it }
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
        tags("Forvaltning")
        description =
            "Hent oversikt over alle feltdefinisjoner som er brukt som kriterie i oppgavekøer og lagrede søk, med antall for hver"
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
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
