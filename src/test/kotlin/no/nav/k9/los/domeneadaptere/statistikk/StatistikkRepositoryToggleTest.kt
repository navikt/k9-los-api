package no.nav.k9.los.domeneadaptere.statistikk

import io.mockk.mockk
import io.mockk.verify
import kotliquery.TransactionalSession
import kotliquery.action.UpdateQueryAction
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test

class StatistikkRepositoryToggleTest {

    @Test
    fun `område som ikke er med registreres ikke i pending`() {
        val tx = mockk<TransactionalSession>()
        val repository = StatistikkRepository(
            mockk(),
            mockk(),
            aktiveOmråder = setOf(Områder.K9),
        )

        repository.bestillDvhSending(
            område = Områder.AKTIVITETSPENGER,
            eksternId = "behandling-1",
            eksternVersjon = "2026-01-01T12:00:00",
            oppgavetypeEksternId = AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode,
            tx = tx,
        )

        verify(exactly = 0) { tx.run(any<UpdateQueryAction>()) }
    }
}
