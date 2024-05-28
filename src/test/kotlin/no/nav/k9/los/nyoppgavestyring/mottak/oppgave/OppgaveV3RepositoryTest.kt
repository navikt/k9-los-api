package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
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
            val oppgaveId = oppgaveV3Repository.nyOppgaveversjon(oppgaveV3, 0, tx)
            oppgaveV3Repository.lagreFeltverdier(oppgaveId, oppgaveV3, tx)
        }

        val lagretOppgave = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentAktivOppgave(
                oppgaveV3.eksternId, oppgaveV3.oppgavetype, tx
                )
        }!!

        assertThat(lagretOppgave.hentVerdi(FeltType.BEHANDLINGUUID.eksternId)).isEqualTo(oppgaveV3.hentVerdi(FeltType.BEHANDLINGUUID.eksternId))
    }

    @Test
    fun `deaktiverOppgaveVersjon`() {
        val oppgaveV3 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test123")
            .lag(1)

        val oppgaveId = transactionalManager.transaction { tx ->
            val oppgaveId = oppgaveV3Repository.nyOppgaveversjon(oppgaveV3, 0, tx)
            oppgaveV3Repository.lagreFeltverdier(oppgaveId, oppgaveV3, tx)
            oppgaveId
        }

        transactionalManager.transaction { tx ->
            oppgaveV3Repository.deaktiverVersjon(oppgaveId, LocalDateTime.now(), tx)
            oppgaveV3Repository.oppdaterOppgavefelterMedOppgavestatus(oppgaveId, aktiv = false, oppgaveV3.status, tx)
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
        assertThat(lagretOppgave.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.aktiv).isEqualTo(false)
    }

    @Test
    fun `ny Oppgaveversjon serie`() {
        val oppgave1 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test123")
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "9001")
            .lag(1)

        val oppgave2 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test123")
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "5015")
            .lag(2)

        val oppgave3 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test123")
            .lag(3, Oppgavestatus.LUKKET)

        transactionalManager.transaction { tx ->
            oppgaveV3Repository.nyOppgaveversjon(oppgave1, tx)
            oppgaveV3Repository.nyOppgaveversjon(oppgave2, tx)
        }
        val lagretOppgave1 = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentOppgaveversjon(
                område = oppgave1.oppgavetype.område,
                eksternId = oppgave1.eksternId,
                eksternVersjon = oppgave1.eksternVersjon,
                tx
                )
        }

        assertThat(lagretOppgave1.aktiv).isFalse()
        assertThat(lagretOppgave1.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.aktiv).isFalse()
        assertThat(lagretOppgave1.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.oppgavestatus).isEqualTo(Oppgavestatus.AAPEN)

        transactionalManager.transaction { tx ->
            oppgaveV3Repository.nyOppgaveversjon(oppgave3, tx)
        }
        val lagretOppgave2 = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentOppgaveversjon(oppgave2.oppgavetype.område, oppgave2.eksternId, oppgave2.eksternVersjon, tx)
        }

        assertThat(lagretOppgave2.aktiv).isFalse()
        assertThat(lagretOppgave2.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.aktiv).isFalse()
        assertThat(lagretOppgave2.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.oppgavestatus).isEqualTo(Oppgavestatus.AAPEN)

        val lagretOppgave3 = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentOppgaveversjon(oppgave3.oppgavetype.område, oppgave3.eksternId, oppgave3.eksternVersjon, tx)
        }

        assertThat(lagretOppgave3.aktiv).isTrue()
        assertThat(lagretOppgave3.status).isEqualTo(Oppgavestatus.LUKKET)
        assertThat(lagretOppgave3.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.aktiv).isTrue()
        assertThat(lagretOppgave3.hentOppgavefeltverdi(FeltType.BEHANDLINGUUID.eksternId)!!.oppgavestatus).isEqualTo(Oppgavestatus.LUKKET)
    }
}