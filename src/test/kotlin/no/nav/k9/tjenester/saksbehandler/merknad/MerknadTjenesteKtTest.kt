package no.nav.k9.tjenester.saksbehandler.merknad

import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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

    private val om = ObjectMapper().configure()

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
        val merknadKode = "VANSKELIG"
        val fritekst = "Markert fordi den står fast"
        val merknadEndret = MerknadEndret(merknadKoder = listOf(merknadKode), fritekst = fritekst)

        runBlocking {
            merknadTjeneste.lagreMerknad(eksternReferanse, merknadEndret)
        }
        val merknad = merknadTjeneste.hentMerknad(eksternReferanse)!!
        assertThat(merknad.merknadKoder.first()).isEqualTo(merknadKode)
        assertThat(merknad.fritekst).isEqualTo(fritekst)
    }

    @Test
    fun `skal kunne endre og slette merknad`() {
        val eksternReferanse = UUID.randomUUID().toString()
        lagreNyBehandlingMedOppgave(eksternReferanse)

        val nyMerknad = MerknadEndret(merknadKoder = listOf("HASTESAK"), fritekst = "OBS trenger hjelp asap")
        runBlocking { merknadTjeneste.lagreMerknad(eksternReferanse, nyMerknad) }
        val original = merknadTjeneste.hentMerknad(eksternReferanse)!!

        val endretMerknad = MerknadEndret(merknadKoder = listOf("VENTESAK", "HASTESAK"), fritekst = "Står fast og haster")
        runBlocking { merknadTjeneste.lagreMerknad(eksternReferanse, endretMerknad) }
        val endret = merknadTjeneste.hentMerknad(eksternReferanse)!!
        assertThat(endret.id!!).isEqualTo(original.id!!)
        assertThat(endret.merknadKoder).containsAll(*endretMerknad.merknadKoder.toTypedArray())
        assertThat(endret.fritekst).isEqualTo(endretMerknad.fritekst)

        val slettMerknad = MerknadEndret(merknadKoder = emptyList(), fritekst = "blalba")
        runBlocking { merknadTjeneste.lagreMerknad(eksternReferanse, slettMerknad) }

        assertThat(merknadTjeneste.hentMerknad(eksternReferanse)).isNull()
        val slettede = oppgaveRepository.hentMerknader(eksternReferanse, inkluderSlettet = true)
        assertThat(slettede).hasSize(1)
        val slettet = slettede.first()
        assertThat(slettet.id!!).isEqualTo(original.id!!)
        assertThat(slettet.merknadKoder).containsAll(*endretMerknad.merknadKoder.toTypedArray())
        assertThat(slettet.fritekst).isEqualTo(endretMerknad.fritekst)
        assertThat(slettet.slettet).isTrue()

    }

    @Test
    fun `api skal lagre og hente merknad`() {
        val eksternReferanse = UUID.randomUUID().toString()

        lagreNyBehandlingMedOppgave(eksternReferanse)

        withTestApplication(Application::merknadTestModule) {
            handleRequest(HttpMethod.Post, "/$eksternReferanse/merknad") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val merknadEndret = MerknadEndret(merknadKoder = listOf("456"), fritekst = "hei")
                setBody(om.writeValueAsString(merknadEndret))

            }.apply {
                assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
            }
            handleRequest(HttpMethod.Get, "/$eksternReferanse/merknad")
                .apply {
                    assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
                    val merknad = om.readValue<MerknadResponse>(response.content!!)
                    assertThat(merknad.merknadKoder).containsExactly("456")
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
            configure()
        }
    }
}

fun ObjectMapper.configure(): ObjectMapper {
    return dusseldorfConfigured()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .registerKotlinModule()
}