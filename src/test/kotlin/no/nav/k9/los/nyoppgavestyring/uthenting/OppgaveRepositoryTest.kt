package no.nav.k9.los.nyoppgavestyring.uthenting

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class OppgaveRepositoryTest : AbstractK9LosIntegrationTest() {

    @Test
    fun hentNyesteOppgaveForEksternId() {
        val aktivOppgaveOppslag = get<AktivOppgaveOppslag>()
        val temporalOppgaveOppslag = get<TemporalOppgaveOppslag>()
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "1234")
            .lagOgLagre()

        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "2345")
            .lagOgLagre()

        val oppgaveTidsserie =
            temporalOppgaveOppslag.hentTidsserie(
                oppgavetypeEksternId = "k9sak",
                oppgaveEksternId = behandlingUuid
            )

        assertThat(oppgaveTidsserie.size).isEqualTo(2)
        assertThat(oppgaveTidsserie[0].hentListeverdi(FeltType.AKSJONSPUNKT.eksternId)[0]).isEqualTo("1234")
        assertThat(oppgaveTidsserie[1].hentListeverdi(FeltType.AKSJONSPUNKT.eksternId)[0]).isEqualTo("2345")

        val nyesteOppgave = aktivOppgaveOppslag.hentAktivOppgave(behandlingUuid, "k9sak")

        // Identifiserer nyeste oppgave basert på aksjonspunktkode 2345
        assertThat(nyesteOppgave.hentListeverdi(FeltType.AKSJONSPUNKT.eksternId)[0]).isEqualTo("2345")
    }
}
