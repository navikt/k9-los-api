package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class OppgaveRepositoryTest : AbstractK9LosIntegrationTest() {

    @Test
    fun hentNyesteOppgaveForEksternId() {
        val oppgaveRepositoryTxWrapper = get<OppgaveRepositoryTxWrapper>()
        val forvaltningRepository = get<ForvaltningRepository>()
        val transactionalManager = get<TransactionalManager>()
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "1234")
            .lagOgLagre()

        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "2345")
            .lagOgLagre()

        val oppgaveTidsserie = transactionalManager.transaction { tx ->
            forvaltningRepository.hentOppgaveTidsserie(
                områdeEksternId = "K9",
                oppgaveTypeEksternId = "k9sak",
                oppgaveEksternId = behandlingUuid,
                tx = tx)
        }

        assertThat(oppgaveTidsserie.size).isEqualTo(2)
        assertThat(oppgaveTidsserie[0].hentListeverdi(FeltType.AKSJONSPUNKT.eksternId).get(0)).isEqualTo("1234")
        assertThat(oppgaveTidsserie[1].hentListeverdi(FeltType.AKSJONSPUNKT.eksternId).get(0)).isEqualTo("2345")

        val nyesteOppgave = oppgaveRepositoryTxWrapper.hentOppgave("K9", behandlingUuid)

        assertThat(nyesteOppgave.versjon).isEqualTo(1)
        assertThat(nyesteOppgave.hentListeverdi(FeltType.AKSJONSPUNKT.eksternId).get(0)).isEqualTo("2345")
    }
}
