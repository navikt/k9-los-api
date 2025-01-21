package no.nav.k9.los.integrasjon.abac

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import com.google.gson.GsonBuilder
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.auditlogger.K9Auditlogger
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.rest.NavHeaders
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

private val gson = GsonBuilder().setPrettyPrinting().create()

private const val XACML_CONTENT_TYPE = "application/xacml+json"
private const val DOMENE = "k9"


class PepClient(
    private val azureGraphService: IAzureGraphService,
    private val config: Configuration,
    private val k9Auditlogger: K9Auditlogger
) : IPepClient {

    private val url = config.abacEndpointUrl
    private val log: Logger = LoggerFactory.getLogger(PepClient::class.java)
    private val cache = Cache<String, Boolean>(cacheSizeLimit = 1000)

    override suspend fun erOppgaveStyrer(): Boolean {
        //TODO inline metode
        return kotlin.coroutines.coroutineContext.idToken().erOppgavebehandler()
    }

    override suspend fun harBasisTilgang(): Boolean {
        //TODO inline metode
        return kotlin.coroutines.coroutineContext.idToken().harBasistilgang()
    }

    override suspend fun kanLeggeUtDriftsmelding(): Boolean {
        //TODO inline metode
        return coroutineContext.idToken().erDrifter()
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean {
        //TODO inline metode
        return kotlin.coroutines.coroutineContext.idToken().erSaksbehandler()
    }

    override suspend fun harTilgangTilKode6(ident: String): Boolean {
        if (ident == kotlin.coroutines.coroutineContext.idToken().getNavIdent()){
            return harTilgangTilKode6();
        }
        val requestBuilder = XacmlRequestBuilder()
            .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
            .addResourceAttribute(RESOURCE_TYPE, TILGANG_TIL_KODE_6)
            .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
            .addAccessSubjectAttribute(SUBJECTID, ident)
            .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
            return evaluate(requestBuilder)
    }

    override suspend fun harTilgangTilKode6(): Boolean {
        //TODO inline metode
        return kotlin.coroutines.coroutineContext.idToken().kanBehandleKode6()
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
        return harTilgang(
            "k9sak",
            azureGraphService.hentIdentTilInnloggetBruker(),
            Action.read,
            oppgave.fagsakSaksnummer,
            oppgave.aktorId,
            oppgave.pleietrengendeAktørId,
            Auditlogging.IKKE_LOGG
        )
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        action: Action,
        auditlogging: Auditlogging
    ): Boolean {
        return harTilgang(
            oppgave.oppgavetype.eksternId,
            azureGraphService.hentIdentTilInnloggetBruker(),
            action,
            oppgave.hentVerdi("saksnummer"),
            oppgave.hentVerdi("aktorId"),
            oppgave.hentVerdi("pleietrengendeAktorId"),
            auditlogging
        )
    }

    override fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        auditlogging: Auditlogging
    ): Boolean {
        return runBlocking {
            harTilgang(
                oppgave.oppgavetype.eksternId,
                saksbehandler.brukerIdent!!,
                action,
                oppgave.hentVerdi("saksnummer"),
                oppgave.hentVerdi("aktorId"),
                oppgave.hentVerdi("pleietrengendeAktorId"),
                auditlogging
            )
        }
    }

    private suspend fun harTilgang(
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
        auditlogging: Auditlogging
    ): Boolean {
        return when (oppgavetype) {
            "k9sak", "k9klage", "k9tilbake" -> {
                val tilgang = evaluate(
                    XacmlRequestBuilder()
                        .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
                        .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
                        .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
                        .addActionAttribute(ACTION_ID, action)
                        .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
                        .addAccessSubjectAttribute(SUBJECTID, identTilInnloggetBruker)
                        .addResourceAttribute(RESOURCE_SAKSNR, saksnummer!!)
                )

                k9Auditlogger.betingetLogging(tilgang, auditlogging) {
                    loggTilgangK9Sak(saksnummer, aktørIdSøker!!, identTilInnloggetBruker, action, tilgang)
                }

                tilgang
            }

            "k9punsj" -> {

                val berørteAktørId = setOfNotNull(aktørIdSøker, aktørIdPleietrengende)
                val builder = XacmlRequestBuilder()
                    .addEnvironmentAttribute(ENVIRONMENT_PEP_ID, "srvk9los")
                    .addResourceAttribute(RESOURCE_DOMENE, DOMENE)
                    .addResourceAttribute(RESOURCE_TYPE, TILGANG_SAK)
                    .addActionAttribute(ACTION_ID, action)
                    .addAccessSubjectAttribute(SUBJECT_TYPE, INTERNBRUKER)
                    .addAccessSubjectAttribute(SUBJECTID, identTilInnloggetBruker)
                    .addResourceAttribute(RESOURCE_AKTØR_ID, berørteAktørId)
                if (config.koinProfile == KoinProfile.PREPROD){
                    val xacmlJson = gson.toJson(builder.build())
                    log.info("Tilgangssforespørsel k9punsj: " + xacmlJson)
                }
                val tilgang = evaluate(builder)

                berørteAktørId.firstOrNull()?.let { aktørId ->
                    k9Auditlogger.betingetLogging(tilgang, auditlogging) {
                        loggTilgangK9Punsj(aktørId, identTilInnloggetBruker, action, tilgang)
                    }
                }

                tilgang
            }

            else -> throw NotImplementedError("Støtter kun tilgangsoppslag på k9klage, k9sak, k9tilbake og k9punsj")
        }
    }

    private suspend fun evaluate(xacmlRequestBuilder: XacmlRequestBuilder): Boolean {
        val xacmlJson = gson.toJson(xacmlRequestBuilder.build())
        val get = cache.get(xacmlJson)
        if (get == null) {
            val result = withContext(Dispatchers.IO + Span.current().asContextElement()) {
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
                    if (config.koinProfile == KoinProfile.PREPROD){
                        log.info("tilgangssvar: $json")
                    }
                    LosObjectMapper.instance.readValue<Response>(json).response[0].decision == "Permit"
                } catch (e: Exception) {
                    log.error(
                        "Feilet deserialisering", e
                    )
                    false
                }
            }
            val now = LocalDateTime.now()
            cache.removeExpiredObjects(now)
            cache.set(xacmlJson, CacheObject(result, now.plusHours(1)))
            return result
        } else {
            return get.value
        }
    }
}

