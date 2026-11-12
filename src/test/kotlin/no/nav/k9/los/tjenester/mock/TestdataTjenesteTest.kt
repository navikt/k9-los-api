package no.nav.k9.los.tjenester.mock

import kotliquery.queryOf
import kotliquery.TransactionalSession
import kotlinx.coroutines.runBlocking
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelUtenOmrådeDto
import no.nav.k9.los.reservasjon.ReservasjonApisTjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import kotlin.test.*
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.Områdesetup as K9Områdesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup as AvpOmrådesetup

class TestdataTjenesteTest : AbstractK9LosIntegrationTest() {
    @BeforeEach
    fun opprettOmråde() {
        get<K9Områdesetup>().setup()
        get<AvpOmrådesetup>().setup()
        localSetup.initSaksbehandlere()
    }

    private fun tjeneste() = TestdataTjeneste(get(), get(), get(), get(), get())

    @Test
    fun `mappingfeil ruller tilbake hendelsesopprettelsen`() {
        val adapter = mockk<EventTilOppgaveAdapter>()
        every { adapter.oppdaterOppgaveForEksternId(any<EventNøkkel>(), any<TransactionalSession>()) } throws IllegalStateException("Mapping feilet")
        val tjeneste = TestdataTjeneste(get(), get(), adapter, get(), get())

        assertFailsWith<IllegalStateException> { tjeneste.opprettOppgave() }
        get<TransactionalManager>().transaction { tx ->
            assertEquals(0L, tx.run(queryOf("select count(*) as antall from event_nokkel")
                .map { it.long("antall") }.asSingle))
        }
    }

    @Test
    fun `oppretter åpne oppgaver med ulike reservasjonsnøkler og rydder bare egne data`() {
        val tjeneste = tjeneste()
        val annenTjeneste = tjeneste()
        val første = tjeneste.opprettOppgave()
        val andre = annenTjeneste.opprettOppgave()
        val txManager = get<TransactionalManager>()
        val repository = get<OppgaveRepository>()

        runBlocking(CoroutineRequestContext(IdTokenLocal(), Områder.K9)) {
            listOf(første, andre).forEach { oppgave ->
                get<ReservasjonApisTjeneste>().reserverOppgave(
                    Områder.K9, false, "Z123456",
                    OppgaveNøkkelUtenOmrådeDto(oppgave.oppgaveEksternId, oppgave.oppgaveTypeEksternId),
                )
            }
        }

        txManager.transaction { tx ->
            val oppgave1 = repository.hentNyesteOppgaveForEksternId(tx, Områder.K9, første.oppgaveEksternId)
            val oppgave2 = repository.hentNyesteOppgaveForEksternId(tx, Områder.K9, andre.oppgaveEksternId)
            assertEquals(Oppgavestatus.AAPEN, oppgave1.status)
            assertEquals(første.saksnummer, oppgave1.hentVerdi("saksnummer"))
            assertNotEquals(oppgave1.reservasjonsnøkkel, oppgave2.reservasjonsnøkkel)
        }

        assertFalse(tjeneste.slettOppgave(andre.id))
        assertTrue(tjeneste.slettOppgave(første.id))
        assertFalse(tjeneste.slettOppgave(første.id))
        txManager.transaction { tx ->
            assertNull(repository.hentNyesteOppgaveForEksternIdHvisFinnes(tx, Områder.K9, første.oppgaveEksternId))
            assertNotNull(repository.hentNyesteOppgaveForEksternIdHvisFinnes(tx, Områder.K9, andre.oppgaveEksternId))
            assertEquals(1L, tx.run(queryOf("select count(*) as antall from reservasjon_v3")
                .map { it.long("antall") }.asSingle))
            listOf(
                "select count(*) as antall from event_nokkel where ekstern_id = :id",
                "select count(*) as antall from oppgave_id_part where oppgave_ekstern_id = :id",
                "select count(*) as antall from oppgave_v3_dvh_pending where ekstern_id = :id",
                "select count(*) as antall from oppgave_pep_cache where ekstern_id = :id",
            ).forEach { sql ->
                assertEquals(0L, tx.run(queryOf(sql, mapOf("id" to første.oppgaveEksternId)).map { it.long("antall") }.asSingle))
            }
        }
        assertTrue(annenTjeneste.slettOppgave(andre.id))
    }
}
