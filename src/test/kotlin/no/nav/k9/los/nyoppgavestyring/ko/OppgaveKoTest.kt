package no.nav.k9.los.nyoppgavestyring.ko

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.query.db.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.time.LocalDate
import java.util.*

class OppgaveKoTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `sjekker at oppgavekø kan opprettes og slettes`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource)

        val oppgaveKo = oppgaveKoRepository.leggTil("Testkø")
        assertThat(oppgaveKo.tittel).isEqualTo("Testkø")

        val oppgaveKoFraDb = oppgaveKoRepository.hent(oppgaveKo.id)
        assertThat(oppgaveKoFraDb).isNotNull()

        oppgaveKoRepository.slett(oppgaveKo.id)
        assertFailure {
            oppgaveKoRepository.hent(oppgaveKo.id)
        }
    }

}