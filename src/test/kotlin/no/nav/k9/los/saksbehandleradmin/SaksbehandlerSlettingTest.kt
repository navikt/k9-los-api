package no.nav.k9.los.saksbehandleradmin

import io.mockk.mockk
import kotliquery.queryOf
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.uttrekk.UttrekkStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaksbehandlerSlettingTest : AbstractPostgresTest() {
    private val transaksjoner = TransactionalManager(dataSource)
    private val områder = OmrådeRepository(dataSource)
    private val repository = SaksbehandlerRepository(dataSource, transaksjoner, områder)
    private val køer = OppgaveKoRepository(dataSource, områder)
    private val tjeneste = SaksbehandlerAdminTjeneste(transaksjoner, repository, køer, mockk(), mockk(), mockk())

    @Test
    fun `koeoppretting returnerer generert id i TransactionalManager-sesjon`() {
        områder.lagre(Områder.K9.eksternId)
        val kø = transaksjoner.transaction { tx -> køer.leggTil(tx, "Test", false, Områder.K9) }

        assertEquals(kø.id, køer.hent(kø.id, false, Områder.K9).id)
        assertEquals("Test", kø.tittel)
    }

    @Test
    fun `id-sletting fjerner bare K9-data for bruker med to omraader`() {
        val id = opprett(listOf(Områder.K9, Områder.AKTIVITETSPENGER))
        val før = snapshot(Områder.AKTIVITETSPENGER)

        tjeneste.slettSaksbehandlerForId(id, TestKontekstFactory.brukerkontekst(Områder.K9))

        assertEquals(før, snapshot(Områder.AKTIVITETSPENGER))
        assertEquals(listOf(Områder.AKTIVITETSPENGER), repository.finnSaksbehandlerMedId(id)!!.områder)
        assertRyddet(id, Områder.K9)
    }

    @Test
    fun `epost-sletting av AP bevarer K9-medlemskap historikk og uttrekk`() {
        val id = opprett(listOf(Områder.K9, Områder.AKTIVITETSPENGER))
        val før = snapshot(Områder.K9)

        tjeneste.slettSaksbehandler("TEST@nav.no", TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER))

        assertEquals(før, snapshot(Områder.K9))
        assertEquals(listOf(Områder.K9), repository.finnSaksbehandlerMedId(id)!!.områder)
        assertRyddet(id, Områder.AKTIVITETSPENGER)
    }

    @Test
    fun `siste K9-omraade sletter bruker og uttrekk inkludert legacy uten kildesoek`() {
        val id = opprett(listOf(Områder.K9))
        transaksjoner.transaction { tx ->
            tx.run(queryOf(
                """insert into uttrekk (status, tittel, query, type_kjoring, laget_av)
                   values (:status, 'legacy', '{}', 'NY', :id)""", mapOf("id" to id, "status" to UttrekkStatus.FULLFØRT.name)
            ).asUpdate)
        }

        tjeneste.slettSaksbehandler("test@nav.no", TestKontekstFactory.brukerkontekst(Områder.K9))

        assertNull(repository.finnSaksbehandlerMedId(id))
        assertRyddet(id, Områder.K9)
        assertEquals(0, antall("select count(*) from uttrekk where laget_av = :id", id))
    }

    @Test
    fun `siste AP-omraade kan slettes uten aa endre annen brukers data`() {
        val id = opprett(listOf(Områder.AKTIVITETSPENGER))
        tjeneste.slettSaksbehandlerForId(id, TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER))
        assertNull(repository.finnSaksbehandlerMedId(id))
        assertRyddet(id, Områder.AKTIVITETSPENGER)
    }

    @Test
    fun `kode6-bruker slettes med matchende rett og rydder ogsaa gamle koemedlemskap`() {
        val id = opprett(listOf(Områder.K9))
        transaksjoner.transaction { tx ->
            tx.run(queryOf("update saksbehandler set skjermet = true where id = :id", mapOf("id" to id)).asUpdate)
        }
        val bruker = TestKontekstFactory.brukerkontekst(
            Områder.K9, tilganger = TestKontekstFactory.ALLE_TILGANGER.copy(harTilgangTilKode6 = true)
        )

        tjeneste.slettSaksbehandlerForId(id, bruker)

        assertNull(repository.finnSaksbehandlerMedId(id))
        assertRyddet(id, Områder.K9)
    }

    @Test
    fun `kjoerende uttrekk ruller tilbake koemedlemskap og all annen opprydding`() {
        val id = opprett(listOf(Områder.K9))
        transaksjoner.transaction { tx ->
            tx.run(queryOf("update uttrekk set status = :status where laget_av = :id",
                mapOf("status" to UttrekkStatus.KJØRER.name, "id" to id)).asUpdate)
        }
        val før = snapshot(Områder.K9)

        assertThrows<IllegalStateException> {
            tjeneste.slettSaksbehandlerForId(id, TestKontekstFactory.brukerkontekst(Områder.K9))
        }

        assertEquals(før, snapshot(Områder.K9))
        assertNotNull(repository.finnSaksbehandlerMedId(id))
    }

    @Test
    fun `annen brukers uttrekk mister ikke kildesoek ved sletting`() {
        val id = opprett(listOf(Områder.K9))
        transaksjoner.transaction { tx ->
            tx.run(queryOf("""update uttrekk set laget_av = (select id from saksbehandler where epost = 'annen@nav.no')
                where laget_av = :id""", mapOf("id" to id)).asUpdate)
        }
        val før = snapshot(Områder.K9)

        assertThrows<IllegalStateException> {
            tjeneste.slettSaksbehandlerForId(id, TestKontekstFactory.brukerkontekst(Områder.K9))
        }

        assertEquals(før, snapshot(Områder.K9))
        assertNotNull(repository.finnSaksbehandlerMedId(id))
    }

    @Test
    fun `FK til historikk i annet omraade stopper global sletting og ruller tilbake`() {
        val id = opprett(listOf(Områder.K9, Områder.AKTIVITETSPENGER))
        // Simuler gammel inkonsistent registrering med historikk, men uten områdekobling.
        transaksjoner.transaction { tx ->
            tx.run(queryOf("delete from lagret_sok where laget_av = :id and omrade_id = (select id from omrade where ekstern_id = 'AKTIVITETSPENGER')", mapOf("id" to id)).asUpdate)
            repository.fjernOmrådeFraSaksbehandler(tx, "test@nav.no", false, Områder.AKTIVITETSPENGER)
        }
        val k9Før = snapshot(Områder.K9)
        val apFør = snapshot(Områder.AKTIVITETSPENGER)

        assertThrows<PSQLException> {
            tjeneste.slettSaksbehandlerForId(id, TestKontekstFactory.brukerkontekst(Områder.K9))
        }

        assertEquals(k9Før, snapshot(Områder.K9))
        assertEquals(apFør, snapshot(Områder.AKTIVITETSPENGER))
        assertNotNull(repository.finnSaksbehandlerMedId(id))
    }

    private fun opprett(områdeliste: List<Områder>): Long {
        områdeliste.forEach { områder.lagre(it.eksternId) }
        val id = repository.opprettSaksbehandler("test@nav.no", områdeliste.first())
        val annen = repository.opprettSaksbehandler("annen@nav.no", områdeliste.first())
        områdeliste.drop(1).forEach {
            repository.leggTilOmråde(id, it)
            repository.leggTilOmråde(annen, it)
        }
        transaksjoner.transaction { tx ->
            områdeliste.forEach { område ->
                val områdeId = områder.hentOmråde(område, tx).id
                val p = mapOf("id" to id, "annen" to annen, "omrade" to områdeId)
                val kø = tx.updateAndReturnGeneratedKey(queryOf(
                    """insert into oppgaveko_v3 (versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave, skjermet, omrade_id)
                       values (0, 'test', '', '{}', false, false, :omrade)""", p
                ))!!
                tx.run(queryOf("insert into oppgaveko_saksbehandler (oppgaveko_v3_id, saksbehandler_id) values (:ko, :id), (:ko, :annen)", p + ("ko" to kø)).asUpdate)
                val søk = tx.updateAndReturnGeneratedKey(queryOf(
                    """insert into lagret_sok (versjon, tittel, beskrivelse, sist_endret, query, laget_av, omrade_id)
                       values (0, 'test', '', localtimestamp, '{}', :id, :omrade)""", p
                ))!!
                if (område == Områder.K9) {
                    tx.run(queryOf("""insert into uttrekk (status, tittel, query, type_kjoring, laget_av, lagret_sok_id)
                        values (:status, 'test', '{}', 'NY', :id, :sok)""", p + mapOf("sok" to søk, "status" to UttrekkStatus.FULLFØRT.name)).asUpdate)
                }
                val reservasjon = tx.updateAndReturnGeneratedKey(queryOf("""insert into reservasjon_v3 (reservertav, reservasjonsnokkel, gyldig_tidsrom, omrade_id)
                    values (:id, :nokkel, tsrange(localtimestamp, localtimestamp + interval '1 day'), :omrade)""",
                    p + ("nokkel" to "test-${område.eksternId}")))!!
                val annenReservasjon = tx.updateAndReturnGeneratedKey(queryOf("""insert into reservasjon_v3 (reservertav, reservasjonsnokkel, gyldig_tidsrom, omrade_id)
                    values (:annen, :nokkel, tsrange(localtimestamp, localtimestamp + interval '1 day'), :omrade)""",
                    p + ("nokkel" to "annen-${område.eksternId}")))!!
                tx.run(queryOf("""insert into reservasjon_v3_endring (annullert_reservasjon_id, ny_reservasjon_id, endretav)
                    values (:reservasjon, :annenReservasjon, :annen), (:annenReservasjon, null, :id)""",
                    p + mapOf("reservasjon" to reservasjon, "annenReservasjon" to annenReservasjon)).asUpdate)
            }
        }
        return id
    }

    private fun assertRyddet(id: Long, område: Områder) {
        val områdeId = områder.hentOmråde(område.eksternId).id
        assertEquals(0, antall("select count(*) from lagret_sok where laget_av = :id and omrade_id = :omrade", id, områdeId))
        assertEquals(0, antall("select count(*) from reservasjon_v3 where reservertav = :id and omrade_id = :omrade", id, områdeId))
        assertEquals(0, antall("select count(*) from oppgaveko_saksbehandler os join oppgaveko_v3 ko on ko.id = os.oppgaveko_v3_id where os.saksbehandler_id = :id and ko.omrade_id = :omrade", id, områdeId))
        assertEquals(1, antall("select count(*) from oppgaveko_saksbehandler os join oppgaveko_v3 ko on ko.id = os.oppgaveko_v3_id where os.saksbehandler_id <> :id and ko.omrade_id = :omrade", id, områdeId))
        assertEquals(1, antall("select count(*) from reservasjon_v3 where reservertav <> :id and omrade_id = :omrade", id, områdeId))
        assertEquals(0, antall("select count(*) from reservasjon_v3_endring re join reservasjon_v3 r on r.id = re.annullert_reservasjon_id where r.omrade_id = :omrade and re.endretav = :id", id, områdeId))
        assertTrue(snapshot(område).isNotEmpty())
    }

    private fun antall(sql: String, id: Long, områdeId: Long? = null): Int = transaksjoner.transaction { tx ->
        tx.run(queryOf(sql, mapOf("id" to id, "omrade" to områdeId)).map { it.int(1) }.asSingle)!!
    }

    private fun snapshot(område: Områder): List<String> = transaksjoner.transaction { tx ->
        tx.run(queryOf("""
            select row_to_json(ko)::text as data from oppgaveko_v3 ko where omrade_id = :omrade
            union all select row_to_json(os)::text from oppgaveko_saksbehandler os join oppgaveko_v3 ko on ko.id = os.oppgaveko_v3_id where ko.omrade_id = :omrade
            union all select row_to_json(ls)::text from lagret_sok ls where omrade_id = :omrade
            union all select row_to_json(u)::text from uttrekk u join lagret_sok ls on ls.id = u.lagret_sok_id where ls.omrade_id = :omrade
            union all select row_to_json(r)::text from reservasjon_v3 r where omrade_id = :omrade
            union all select row_to_json(re)::text from reservasjon_v3_endring re join reservasjon_v3 r on r.id = re.annullert_reservasjon_id where r.omrade_id = :omrade
            order by data
        """.trimIndent(), mapOf("omrade" to områder.hentOmråde(område, tx).id)).map { it.string("data") }.asList)
    }
}
