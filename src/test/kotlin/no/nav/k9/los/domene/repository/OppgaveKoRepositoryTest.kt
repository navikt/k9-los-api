package no.nav.k9.los.domene.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.kodeverk.KøSortering
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.qualifier.named
import org.koin.test.get
import java.time.LocalDate
import java.util.*

internal class OppgaveKoRepositoryTest : AbstractK9LosIntegrationTest() {

    val pep = mockk<IPepClient>()
    lateinit var oppgaveKøRepository: OppgaveKøRepository

    @BeforeEach
    fun setup() {
        oppgaveKøRepository = OppgaveKøRepository(
            dataSource = get(),
            oppgaveKøOppdatert = get(named("oppgaveKøOppdatert")),
            oppgaveRefreshChannel = get(named("oppgaveRefreshChannel")),
            pepClient = pep
        )
    }

    @Test
    fun `Saksbehandler uten kode6 skal ikke ha tilgang til kode6-ko`() {
        val id = lagOppgavekø(oppgaveKøRepository, kode6 = true)

        runBlocking {
            assertThrows<IllegalStateException>("") {
                coEvery { pep.harTilgangTilKode6() } returns false
                oppgaveKøRepository.hentOppgavekø(id)
            }
        }
    }

    @Test
    fun `Saksbehandler med kode6 skal ha tilgang til kode6-ko`() {
        val id = lagOppgavekø(oppgaveKøRepository, kode6 = true)

        runBlocking {
            coEvery { pep.harTilgangTilKode6() } returns true
            val oppgavekø = oppgaveKøRepository.hentOppgavekø(id)
            assertThat(oppgavekø.id).isEqualTo(id)
            assertThat(oppgavekø.kode6).isTrue()
        }
    }

    @Test
    fun `Saksbehandler uten kode6 skal ha tilgang til ko som ikke er kode6`() {
        val id = lagOppgavekø(oppgaveKøRepository, kode6 = false)

        runBlocking {
            coEvery { pep.harTilgangTilKode6() } returns false
            val oppgavekø = oppgaveKøRepository.hentOppgavekø(id)
            assertThat(oppgavekø.id).isEqualTo(id)
            assertThat(oppgavekø.kode6).isFalse()
        }
    }

    @Test
    fun `Saksbehandler med kode6 skal ikke ha tilgang til ko som ikke er kode6`() {
        val id = lagOppgavekø(oppgaveKøRepository, kode6 = false)

        runBlocking {
            assertThrows<IllegalStateException>("") {
                coEvery { pep.harTilgangTilKode6() } returns true
                oppgaveKøRepository.hentOppgavekø(id)
            }
        }
    }

    private fun lagOppgavekø(oppgaveKøRepository: OppgaveKøRepository, kode6: Boolean): UUID {
        val id = UUID.randomUUID()

        val oppgavekø = OppgaveKø(
            id = id,
            navn = "",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.FORSTE_STONADSDAG,
            saksbehandlere = mutableListOf(),
        )

        runBlocking {
            coEvery { pep.harTilgangTilKode6() } returns kode6
            oppgaveKøRepository.lagre(id) { oppgavekø }
        }

        return id
    }
}