package no.nav.k9.tjenester.saksbehandler.merknad

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.AbstractPostgresTest
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.v2.Behandling
import no.nav.k9.domene.lager.oppgave.v2.Ident
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OpprettOppgave
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.kodeverk.behandling.BehandlingType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.time.LocalDateTime
import java.util.*

internal class MerknadTjenesteKtTest : AbstractPostgresTest(), KoinTest  {

    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource))
    }

    private val om = ObjectMapper()

    private lateinit var oppgaveRepository : OppgaveRepositoryV2
    private lateinit var tm : TransactionalManager
    private lateinit var merknadTjeneste: MerknadTjeneste

    @BeforeEach
    fun setup() {
        oppgaveRepository = get()
        tm = get()
        merknadTjeneste = get()
    }

    @Test
    fun `skal lagre riktig merknad`() {
        val eksternReferanse = UUID.randomUUID().toString()
        lagreNyBehandlingMedOppgave(eksternReferanse)
        val merknadKode = "456"
        val fritekst = "hei"
        val merknadEndret = MerknadEndret(id = null, merknadKoder = listOf(merknadKode), fritekst = fritekst)

        runBlocking {
            merknadTjeneste.lagreMerknad(eksternReferanse, merknadEndret)
        }
        val merknader = merknadTjeneste.hentMerknad(eksternReferanse)
        assertThat(merknader).hasSize(1)
        assertThat(merknader.first().merknadKoder.first()).isEqualTo(merknadKode)
        assertThat(merknader.first().fritekst).isEqualTo(fritekst)
    }

    @Test
    fun `api skal lagre og hente merknad`() {
        val eksternReferanse = UUID.randomUUID().toString()

        lagreNyBehandlingMedOppgave(eksternReferanse)

        withTestApplication(Application::merknadTestModule) {
            handleRequest(HttpMethod.Post, "/merknad/$eksternReferanse") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val merknadEndret = MerknadEndret(id = null, merknadKoder = listOf("456"), fritekst = "hei")
                setBody(om.writeValueAsString(merknadEndret))

            }.apply {
                assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
            }
            handleRequest(HttpMethod.Get, "/merknad/$eksternReferanse")
                .apply {
                    assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
                    val merknader = om.readValue(response.content, object : TypeReference<List<JsonNode>>() {})
                    assertThat(merknader).hasSize(1)
                }
        }
    }

    private fun lagreNyBehandlingMedOppgave(eksternReferanse: String) {
        val behandling = Behandling.ny(
            eksternReferanse = eksternReferanse,
            fagsystem = Fagsystem.K9SAK,
            ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            behandlingType = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            søkersId = Ident("123", Ident.IdType.AKTØRID),
            opprettet = LocalDateTime.now()
        )

        behandling.nyHendelse(OpprettOppgave(LocalDateTime.now(), "9001", null))

        tm.transaction {
            oppgaveRepository.lagre(behandling, it)
        }
    }
}




private fun Application.merknadTestModule() {
    routing {
        MerknadApi()
    }
    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
        }
    }
}
