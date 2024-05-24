package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class OppgaveV3RepositoryTest : AbstractK9LosIntegrationTest() {
    private lateinit var oppgaveV3Repository: OppgaveV3Repository
    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        oppgaveV3Repository = get<OppgaveV3Repository>()
        transactionalManager = get<TransactionalManager>()
    }

    @Test
    fun `lagre oppgaveVersjon`() {
        val oppgaveV3 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test123")
            .lag(1)

        transactionalManager.transaction { tx ->
            oppgaveV3Repository.nyOppgaveversjon(oppgaveV3, 0, tx)
        }

        val lagretOppgave = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentAktivOppgave(
                oppgaveV3.eksternId, oppgaveV3.oppgavetype, tx
                )
        }!!

        assertThat(oppgaveV3.hentFelt(FeltType.BEHANDLINGUUID.eksternId)).isEqualTo(lagretOppgave.hentFelt(FeltType.BEHANDLINGUUID.eksternId))
    }

    @Test
    fun `deaktiverOppgaveVersjon`() {
        val oppgaveV3 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test123")
            .lag(1)

        val oppgaveId = transactionalManager.transaction { tx ->
            oppgaveV3Repository.nyOppgaveversjon(oppgaveV3, 0, tx)
        }

        transactionalManager.transaction { tx ->
            oppgaveV3Repository.deaktiverVersjon(oppgaveId, LocalDateTime.now(), tx)
        }

        val lagretOppgave = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentOppgaveversjon(
                oppgaveV3.oppgavetype.område,
                oppgaveV3.eksternId,
                oppgaveV3.eksternVersjon,
                tx
            )
        }

        assertThat(lagretOppgave.aktiv).isEqualTo(false)
        assertThat(lagretOppgave.hentFelt(FeltType.BEHANDLINGUUID.eksternId).aktiv).isEqualTo(false)
    }
}