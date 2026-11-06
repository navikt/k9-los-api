package no.nav.k9.los.infrastruktur.abac

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import io.mockk.*
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonGrupperDto
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerOperasjonGrupperDto
import org.junit.jupiter.api.Test
import java.util.UUID

class SifAbacPdpKlientAktivitetspengerTest {
    private val configuration = mockk<Configuration> {
        every { sifAbacPdpUrl() } returns "http://pdp"
        every { sifAbacPdpScope() } returns "api://pdp/.default"
    }
    private val tokens = mockk<AccessTokenClient> {
        every { getClientCredentialsAccessToken(setOf("api://pdp/.default")) } returns AccessTokenResponse("systemtoken", 3600, "Bearer")
    }
    private val grupper = setOf(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val sak = SaksnummerDto("123456789")
    private val aktører = listOf(no.nav.sif.abac.kontrakt.person.AktørId("1234567890123"))

    @Test
    fun `sak bruker AVP-endepunkt systemtoken og mottakers grupper`() = testApplication {
        externalServices {
            hosts("http://pdp") {
                routing {
                    post("/api/tilgangskontroll/v2/aktivitetspenger/sak-grupper") {
                        call.request.headers[HttpHeaders.Authorization] shouldBe "Bearer systemtoken"
                        val dto = LosObjectMapper.instance.readValue<SaksnummerOperasjonGrupperDto>(call.receiveText())
                        dto.ident() shouldBe "Z123456"
                        dto.rettighetsgrupper().toSet() shouldBe grupper
                        dto.saksnummer() shouldBe sak
                        dto.operasjon().resource() shouldBe ResourceType.FAGSAK
                        dto.operasjon().action() shouldBe BeskyttetRessursActionAttributt.RESERVER
                        call.respondText("""{"harTilgang":true,"årsakerForIkkeTilgang":[]}""", ContentType.Application.Json)
                    }
                }
            }
        }
        val klient = SifAbacPdpKlientAktivitetspenger(configuration, tokens, client)

        klient.harTilgangTilSak(Action.reserver, sak, "Z123456", grupper) shouldBe true

        verify(exactly = 1) { tokens.getClientCredentialsAccessToken(any()) }
        verify(exactly = 0) { tokens.getOnBehalfOfAccessToken(any(), any()) }
    }

    @Test
    fun `personer sender aktører og viderefører avslag`() = testApplication {
        externalServices {
            hosts("http://pdp") {
                routing {
                    post("/api/tilgangskontroll/v2/aktivitetspenger/personer-grupper") {
                        call.request.headers[HttpHeaders.Authorization] shouldBe "Bearer systemtoken"
                        val dto = LosObjectMapper.instance.readValue<PersonerOperasjonGrupperDto>(call.receiveText())
                        dto.ident() shouldBe "Z123456"
                        dto.rettighetsgrupper().toSet() shouldBe grupper
                        dto.aktørIder() shouldBe aktører
                        dto.personIdenter() shouldBe emptyList()
                        dto.operasjon().action() shouldBe BeskyttetRessursActionAttributt.READ
                        call.respondText("""{"harTilgang":false,"årsakerForIkkeTilgang":[]}""", ContentType.Application.Json)
                    }
                }
            }
        }
        val klient = SifAbacPdpKlientAktivitetspenger(configuration, tokens, client)

        klient.harTilgangTilPersoner(Action.read, aktører, "Z123456", grupper) shouldBe false
    }

    @Test
    fun `HTTP-feil gir exception og aldri tilgang`() = testApplication {
        externalServices {
            hosts("http://pdp") {
                routing {
                    post("/api/tilgangskontroll/v2/aktivitetspenger/sak-grupper") {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                    }
                }
            }
        }
        val klient = SifAbacPdpKlientAktivitetspenger(configuration, tokens, client)

        shouldThrow<SifAbacPdpHttpException> {
            klient.harTilgangTilSak(Action.reserver, sak, "Z123456", grupper)
        }
    }

    @Test
    fun `ugyldig ident tomt saksnummer og manglende personer avvises før tokenhenting`() = testApplication {
        val klient = SifAbacPdpKlientAktivitetspenger(configuration, tokens, client)
        shouldThrow<IllegalArgumentException> { klient.harTilgangTilSak(Action.read, sak, "ugyldig", grupper) }
        shouldThrow<IllegalArgumentException> { klient.harTilgangTilSak(Action.read, SaksnummerDto(" "), "Z123456", grupper) }
        shouldThrow<IllegalArgumentException> { klient.harTilgangTilPersoner(Action.read, aktører, "ugyldig", grupper) }
        shouldThrow<IllegalArgumentException> { klient.harTilgangTilPersoner(Action.read, emptyList(), "Z123456", grupper) }
        verify { tokens wasNot Called }
    }
}
