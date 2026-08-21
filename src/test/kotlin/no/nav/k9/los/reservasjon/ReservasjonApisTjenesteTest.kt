package no.nav.k9.los.reservasjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.AktivOppgaveOppslag
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReservasjonApisTjenesteTest {
    @Test
    fun `ny reservasjonsmetode bygger alle oppgaver i ett builder-kall`() = runBlocking {
        val reservasjonV3Tjeneste = mockk<ReservasjonV3Tjeneste>()
        val builder = mockk<OppgaveSammendragDtoBuilder>()
        val oppgave1 = mockk<Oppgave>()
        val oppgave2 = mockk<Oppgave>()
        val sammendrag1 = mockk<OppgaveSammendragDto>()
        val sammendrag2 = mockk<OppgaveSammendragDto>()
        val saksbehandler = Saksbehandler(1, "Z123456", "Saks Behandler", "saks@nav.no", null, listOf(Områder.K9))
        val brukerkontekst = TestKontekstFactory.brukerkontekst(Områder.K9)
        val nå = LocalDateTime.parse("2026-08-12T09:00:00")
        val reservasjon1 = ReservasjonV3(1, "r1", "", nå, nå.plusDays(1), null, Områder.K9)
        val reservasjon2 = ReservasjonV3(1, "r2", "", nå, nå.plusDays(1), null, Områder.K9)
        every { reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(1) } returns listOf(
            ReservasjonV3MedOppgaver(reservasjon1, listOf(oppgave1)),
            ReservasjonV3MedOppgaver(reservasjon2, listOf(oppgave2)),
        )
        coEvery { builder.bygg(listOf(oppgave1, oppgave2), any(), emptyMap()) } returns listOf(sammendrag1, sammendrag2)
        val tjeneste = ReservasjonApisTjeneste(
            mockk<SaksbehandlerRepository>(relaxed = true),
            reservasjonV3Tjeneste,
            mockk<TransactionalManager>(),
            mockk<ReservasjonV3DtoBuilder>(),
            mockk<AktivOppgaveOppslag>(),
            mockk<IPepClient>(),
            builder,
        )

        val resultat = tjeneste.hentReserverteOppgaverSammendragForSaksbehandler(saksbehandler, brukerkontekst)

        assertThat(resultat.map { it.oppgaver.single() }).isEqualTo(listOf(sammendrag1, sammendrag2))
        coVerify(exactly = 1) { builder.bygg(listOf(oppgave1, oppgave2), brukerkontekst, emptyMap()) }
    }
}
