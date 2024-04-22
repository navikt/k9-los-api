package no.nav.k9.los.integrasjon.abac

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import com.google.gson.GsonBuilder
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.audit.*
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.rest.NavHeaders
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

private val gson = GsonBuilder().setPrettyPrinting().create()

private const val XACML_CONTENT_TYPE = "application/xacml+json"
private const val DOMENE = "k9"


class PepClient constructor(
    private val azureGraphService: IAzureGraphService,
    private val auditlogger: Auditlogger,
    private val config: Configuration
) : IPepClient {

    private val url = config.abacEndpointUrl
    private val log: Logger = LoggerFactory.getLogger(PepClient::class.java)
    private val cache = Cache<String, Boolean>()

    override suspend fun erOppgaveStyrer(): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, OPPGAVESTYRER)
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, azureGraphService.hentIdentTilInnloggetBruker())
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")

        return evaluate(requestBuilder)
    }

    override suspend fun harBasisTilgang(): Boolean {

        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, BASIS_TILGANG)
            .addActionAttribute(ACTION_ID, "read")
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, azureGraphService.hentIdentTilInnloggetBruker())
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")

        return evaluate(requestBuilder)
    }

    override suspend fun kanLeggeUtDriftsmelding(): Boolean {

        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, DRIFTSMELDING)
            .addActionAttribute(ACTION_ID, "create")
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, azureGraphService.hentIdentTilInnloggetBruker())
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")

        return evaluate(requestBuilder)
    }

    override suspend fun harTilgangTilLesSak(
        fagsakNummer: String,
        aktørid: String
    ): Boolean {
        val identTilInnloggetBruker = azureGraphService.hentIdentTilInnloggetBruker()
        if (identTilInnloggetBruker.isEmpty()) {
            log.warn("Ingen innlogget bruker")
            return false
        }
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
            .addActionAttribute(ACTION_ID, "read")
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, identTilInnloggetBruker)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_SAKSNR, fagsakNummer)
        val decision = evaluate(requestBuilder)

        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = "INFO"
                ), fields = setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, "read"),
                    CefField(CefFieldName.ABAC_RESOURCE_TYPE, TILGANG_SAK),
                    CefField(CefFieldName.ABAC_ACTION, "read"),
                    CefField(CefFieldName.USER_ID, identTilInnloggetBruker),
                    CefField(CefFieldName.BERORT_BRUKER_ID, aktørid),

                    CefField(CefFieldName.BEHANDLING_VERDI, "behandlingsid"),
                    CefField(CefFieldName.BEHANDLING_LABEL, "Behandling"),
                    CefField(CefFieldName.SAKSNUMMER_VERDI, fagsakNummer),
                    CefField(CefFieldName.SAKSNUMMER_LABEL, "Saksnummer")
                )
            )
        )

        return decision
    }

    override fun harTilgangTilLesSak(
        fagsakNummer: String,
        aktørid: String,
        bruker: Saksbehandler
    ): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
            .addActionAttribute(ACTION_ID, "read")
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, bruker.brukerIdent!!)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_SAKSNR, fagsakNummer)
        val decision = runBlocking {
            evaluate(requestBuilder)
        }

        auditlogger.logg(
            Auditdata(
                header = AuditdataHeader(
                    vendor = auditlogger.defaultVendor,
                    product = auditlogger.defaultProduct,
                    eventClassId = EventClassId.AUDIT_SEARCH,
                    name = "ABAC Sporingslogg",
                    severity = "INFO"
                ), fields = setOf(
                    CefField(CefFieldName.EVENT_TIME, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000L),
                    CefField(CefFieldName.REQUEST, "read"),
                    CefField(CefFieldName.ABAC_RESOURCE_TYPE, TILGANG_SAK),
                    CefField(CefFieldName.ABAC_ACTION, "read"),
                    CefField(CefFieldName.USER_ID, bruker.brukerIdent!!),
                    CefField(CefFieldName.BERORT_BRUKER_ID, aktørid),

                    CefField(CefFieldName.BEHANDLING_VERDI, "behandlingsid"),
                    CefField(CefFieldName.BEHANDLING_LABEL, "Behandling"),
                    CefField(CefFieldName.SAKSNUMMER_VERDI, fagsakNummer),
                    CefField(CefFieldName.SAKSNUMMER_LABEL, "Saksnummer")
                )
            )
        )

        return decision
    }

    override suspend fun harTilgangTilReservingAvOppgaver(): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
            .addActionAttribute(ACTION_ID, "reserver")
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, azureGraphService.hentIdentTilInnloggetBruker())
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")

        return evaluate(requestBuilder)
    }

    override suspend fun harTilgangTilKode6(ident: String): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_TIL_KODE_6)
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, ident)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")

            return evaluate(requestBuilder)
    }

    override suspend fun harTilgangTilKode6(): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_TIL_KODE_6)
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, azureGraphService.hentIdentTilInnloggetBruker())
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
        return evaluate(requestBuilder)
    }

    override suspend fun kanSendeSakTilStatistikk(
        fagsakNummer: String
    ): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
            .addActionAttribute(ACTION_ID, "read")
            .addAccessSubjectAttribute(SUBJECT_TYPE, KAFKATOPIC)
            .addAccessSubjectAttribute(SUBJECTID, KAFKATOPIC_STATISTIKK)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_SAKSNR, fagsakNummer)

        return evaluate(requestBuilder)
    }

    override suspend fun erSakKode6(fagsakNummer: String): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK_KODE6)
            .addAccessSubjectAttribute(SUBJECT_TYPE, NONE)
            .addAccessSubjectAttribute(SUBJECTID, NONE)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_SAKSNR, fagsakNummer)

        return !evaluate(requestBuilder)
    }

    override suspend fun erAktørKode6(aktørid: String): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK_KODE6)
            .addAccessSubjectAttribute(SUBJECT_TYPE, NONE)
            .addAccessSubjectAttribute(SUBJECTID, NONE)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_AKTØR_ID, aktørid)

        return !evaluate(requestBuilder)
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK_KODE7OGEGENANSATT)
            .addAccessSubjectAttribute(SUBJECT_TYPE, NONE)
            .addAccessSubjectAttribute(SUBJECTID, NONE)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_SAKSNR, fagsakNummer)

        return !evaluate(requestBuilder)
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean {
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK_KODE7OGEGENANSATT)
            .addAccessSubjectAttribute(SUBJECT_TYPE, NONE)
            .addAccessSubjectAttribute(SUBJECTID, NONE)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            .addResourceAttribute(RESOURCE_AKTØR_ID, aktørid)

        return !evaluate(requestBuilder)
    }

    override suspend fun harTilgangTilOppgave(oppgave: Oppgave): Boolean {
        return !oppgave.harFagSaksNummer() || harTilgangTilLesSak(
            fagsakNummer = oppgave.fagsakSaksnummer,
            aktørid = oppgave.aktorId
        )
    }

    override fun harTilgangTilOppgaveV3(oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave, bruker: Saksbehandler): Boolean {
         if (oppgave.hentVerdi("saksnummer") == null) {
             return true
         } else return runBlocking {
             harTilgangTilLesSak(oppgave.hentVerdi("saksnummer")!!, oppgave.hentVerdi("aktorId")!!, bruker)
         }
    }

    override suspend fun harTilgangTilÅReservereOppgave(oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave, bruker: Saksbehandler) : Boolean {
        val requestBuilder = when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> {
                XacmlRequestBuilder()
                    .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
                    .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
                    .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
                    .addActionAttribute(ACTION_ID, "update")
                    .addResourceAttribute("no.nav.abac.attributter.resource.k9.behandlings_uuid", oppgave.eksternId)  //TODO los skal ikke kjenne til denne detaljen. Oppgavetype må utvides med attributtreferanse
                    .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
                    .addAccessSubjectAttribute(SUBJECTID, bruker.brukerIdent!!)
            }
            "k9klage" -> {
                XacmlRequestBuilder()
                    .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
                    .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
                    .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
                    .addActionAttribute(ACTION_ID, "create")
                    .addResourceAttribute("no.nav.abac.attributter.resource.k9.saksnr", oppgave.hentVerdi("saksnummer")!!)  //TODO los skal ikke kjenne til denne detaljen. Oppgavetype må utvides med attributtreferanse
                    .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
                    .addAccessSubjectAttribute(SUBJECTID, bruker.brukerIdent!!)
            }
            else -> throw NotImplementedError("Støtter kun tilgangsoppslag på k9klage og k9sak")
        }

        val tilgang = evaluate(requestBuilder)
        if (KoinProfile.PREPROD == config.koinProfile() && !tilgang) {
            log.warn("Ikke tilgang til å reservere oppgaver! Spørring: ${requestBuilder.build()}")
        }
        return tilgang
    }

    private suspend fun evaluate(xacmlRequestBuilder: XacmlRequestBuilder): Boolean {
        val xacmlJson = gson.toJson(xacmlRequestBuilder.build())
        val get = cache.get(xacmlJson)
        if (get == null) {
            val result = withContext(Dispatchers.IO) {
                val httpRequest = url
                    .httpPost()
                    .authentication()
                    .basic(config.abacUsername, config.abacPassword)
                    .body(
                        xacmlJson
                    )
                    .header(
                        HttpHeaders.Accept to "application/json",
                        HttpHeaders.ContentType to XACML_CONTENT_TYPE,
                        NavHeaders.CallId to UUID.randomUUID().toString()
                    )
                val json = Retry.retry(
                    operation = "evaluer abac",
                    initialDelay = Duration.ofMillis(200),
                    factor = 2.0,
                    logger = log
                ) {
                    val (request, _, result) = Operation.monitored(
                        app = "k9-los-api",
                        operation = "evaluate abac",
                        resultResolver = { 200 == it.second.statusCode }
                    ) { httpRequest.awaitStringResponseResult() }

                    result.fold(
                        { success -> success },
                        { error ->
                            log.error(
                                "Error response = '${
                                    error.response.body()
                                        .asString("text/plain")
                                }' fra '${request.url}'"
                            )
                            log.error(error.toString())
                            throw IllegalStateException("Feil ved evaluering av abac.")
                        }
                    )
                }
                //  log.info("abac result: $json \n\n $xacmlJson\n\n" + httpRequest.toString())
                try {
                    LosObjectMapper.instance.readValue<Response>(json).response[0].decision == "Permit"
                } catch (e: Exception) {
                    log.error(
                        "Feilet deserialisering", e
                    )
                    false
                }
            }
            cache.set(xacmlJson, CacheObject(result, LocalDateTime.now().plusHours(1)))
            return result
        } else {
            return get.value
        }
    }
}
