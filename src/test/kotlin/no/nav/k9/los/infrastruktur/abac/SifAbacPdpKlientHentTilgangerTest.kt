package no.nav.k9.los.infrastruktur.abac

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal class SifAbacPdpKlientHentTilgangerTest {
    private val k9 = mockk<ISifAbacPdpKlient>()
    private val aktivitetspenger = mockk<ISifAbacPdpKlient>()
    private val ung = mockk<ISifAbacPdpKlient>()
    private val klient = SifAbacPdpKlient(k9, aktivitetspenger, ung, hentTilgangerTimeout = 1.seconds)
    private val idToken = token()
    private val tilganger = Tilganger(
        basis = true,
        kode6 = false,
        oppgavestyring = false,
        reservering = true,
        drift = false,
    )

    @Test
    fun `avbryter hengende kall og cacher ikke timeout`() = runTest {
        var avbrutt = false
        coEvery { k9.hentTilganger(idToken) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                avbrutt = true
            }
        }

        shouldThrow<SifAbacPdpUtilgjengeligException> { klient.hentTilganger(Områder.K9, idToken) }
        avbrutt shouldBe true

        coEvery { k9.hentTilganger(idToken) } returns tilganger
        klient.hentTilganger(Områder.K9, idToken) shouldBe tilganger
        coVerify(exactly = 2) { k9.hentTilganger(idToken) }
    }

    @Test
    fun `gjenbruker tilganger for samme område ident og token`() = runTest {
        val sammeToken = token()
        coEvery { k9.hentTilganger(idToken) } returns tilganger

        klient.hentTilganger(Områder.K9, idToken) shouldBe tilganger
        klient.hentTilganger(Områder.K9, sammeToken) shouldBe tilganger

        coVerify(exactly = 1) { k9.hentTilganger(any()) }
    }

    @Test
    fun `holder tilganger for samme bruker atskilt mellom områder`() = runTest {
        val avpTilganger = tilganger.copy(basis = false, reservering = false)
        coEvery { k9.hentTilganger(idToken) } returns tilganger
        coEvery { aktivitetspenger.hentTilganger(idToken) } returns avpTilganger

        klient.hentTilganger(Områder.K9, idToken) shouldBe tilganger
        klient.hentTilganger(Områder.AKTIVITETSPENGER, idToken) shouldBe avpTilganger
        klient.hentTilganger(Områder.K9, idToken) shouldBe tilganger
        klient.hentTilganger(Områder.AKTIVITETSPENGER, idToken) shouldBe avpTilganger

        coVerify(exactly = 1) { k9.hentTilganger(idToken) }
        coVerify(exactly = 1) { aktivitetspenger.hentTilganger(idToken) }
    }

    @Test
    fun `henter tilganger på nytt ved nytt token for samme bruker`() = runTest {
        val nyttToken = token(tokenId = "nytt-token")
        val nyeTilganger = tilganger.copy(kode6 = true)
        coEvery { k9.hentTilganger(idToken) } returns tilganger
        coEvery { k9.hentTilganger(nyttToken) } returns nyeTilganger

        klient.hentTilganger(Områder.K9, idToken) shouldBe tilganger
        klient.hentTilganger(Områder.K9, nyttToken) shouldBe nyeTilganger

        coVerify(exactly = 1) { k9.hentTilganger(idToken) }
        coVerify(exactly = 1) { k9.hentTilganger(nyttToken) }
    }

    @Test
    fun `holder tilganger atskilt mellom identer selv med samme token-id`() = runTest {
        val annenBruker = token(navIdent = "Z654321")
        val andreTilganger = tilganger.copy(basis = false, reservering = false)
        coEvery { k9.hentTilganger(idToken) } returns tilganger
        coEvery { k9.hentTilganger(annenBruker) } returns andreTilganger

        klient.hentTilganger(Områder.K9, idToken) shouldBe tilganger
        klient.hentTilganger(Områder.K9, annenBruker) shouldBe andreTilganger

        coVerify(exactly = 1) { k9.hentTilganger(idToken) }
        coVerify(exactly = 1) { k9.hentTilganger(annenBruker) }
    }

    @Test
    fun `ruter eksplisitt område uten request context`() = runTest {
        val aktørId = AktørId("1234567890123")
        val saksnummer = SaksnummerDto("123456789")
        val grupper = setOf(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        coEvery { aktivitetspenger.diskresjonskoderPerson(aktørId) } returns setOf(Diskresjonskode.KODE6)
        coEvery { aktivitetspenger.harTilgangTilSak(Action.read, saksnummer, idToken) } returns true
        coEvery { k9.harTilgangTilSak(Action.reserver, saksnummer, "Z123456", grupper) } returns true

        klient.diskresjonskoderPerson(Områder.AKTIVITETSPENGER, aktørId) shouldBe setOf(Diskresjonskode.KODE6)
        klient.harTilgangTilSak(Områder.AKTIVITETSPENGER, Action.read, saksnummer, idToken) shouldBe true
        klient.harTilgangTilSak(Områder.K9, Action.reserver, saksnummer, "Z123456", grupper) shouldBe true

        coVerify(exactly = 1) { aktivitetspenger.diskresjonskoderPerson(aktørId) }
        coVerify(exactly = 1) { aktivitetspenger.harTilgangTilSak(Action.read, saksnummer, idToken) }
        coVerify(exactly = 1) { k9.harTilgangTilSak(Action.reserver, saksnummer, "Z123456", grupper) }
    }

    @Test
    fun `ruter diskresjonskoder for ungdomsprogramytelsen til UNG-klienten`() = runTest {
        val aktørId = AktørId("1234567890123")
        val saksnummer = SaksnummerDto("123456789")

        coEvery { ung.diskresjonskoderPerson(aktørId) } returns setOf(Diskresjonskode.KODE6)
        coEvery { ung.diskresjonskoderSak(saksnummer) } returns setOf(Diskresjonskode.SKJERMET)

        klient.diskresjonskoderPerson(Områder.UNGDOMSPROGRAMYTELSEN, aktørId) shouldBe setOf(Diskresjonskode.KODE6)
        klient.diskresjonskoderSak(Områder.UNGDOMSPROGRAMYTELSEN, saksnummer) shouldBe setOf(Diskresjonskode.SKJERMET)

        coVerify(exactly = 1) { ung.diskresjonskoderPerson(aktørId) }
        coVerify(exactly = 1) { ung.diskresjonskoderSak(saksnummer) }
        coVerify { listOf(k9, aktivitetspenger) wasNot Called }
    }

    private fun token(navIdent: String = "Z123456", tokenId: String = "token-id") = mockk<IIdToken> {
        every { getNavIdent() } returns navIdent
        every { getTokenId() } returns tokenId
    }
}
