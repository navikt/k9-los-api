package no.nav.k9.los.oppgaveuthenting.query.db

import kotliquery.queryOf
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator.IN
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator.NOT_IN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonbeskyttelseFiltermigreringTest : AbstractPostgresTest() {
    @Test
    fun `migrerer køer og lagrede søk én gang ved oppstart`() {
        val txManager = TransactionalManager(dataSource)
        val gammeltFilter = FeltverdiOppgavefilter(null, "personbeskyttelse", IN, listOf("UGRADERT"))
        val gammelQuery = OppgaveQuery(listOf(gammeltFilter))
        val mapper = LosObjectMapper.instance
        val (køId, søkId) = txManager.transaction { tx ->
            val køId = tx.updateAndReturnGeneratedKey(queryOf(
                """
                INSERT INTO oppgaveko_v3(versjon, tittel, beskrivelse, query, fritt_valg_av_oppgave)
                VALUES (1, 'Standard', '', :query, false)
                """.trimIndent(),
                mapOf("query" to mapper.writeValueAsString(gammelQuery)),
            ))!!
            tx.run(queryOf(
                "insert into saksbehandler(epost, skjermet) values ('test@example.invalid', false)"
            ).asUpdate)
            val saksbehandlerId = tx.run(queryOf("select id from saksbehandler where epost = 'test@example.invalid'")
                .map { it.long("id") }.asSingle)!!
            tx.run(queryOf(
                """
                INSERT INTO saksbehandler_omrade(saksbehandler_id, omrade_id)
                SELECT :saksbehandlerId, id FROM omrade WHERE ekstern_id = 'K9'
                """.trimIndent(),
                mapOf("saksbehandlerId" to saksbehandlerId),
            ).asUpdate)
            val søkId = tx.updateAndReturnGeneratedKey(queryOf(
                """
                INSERT INTO lagret_sok(versjon, tittel, beskrivelse, sist_endret, query, laget_av)
                VALUES (1, 'Lagret søk', '', localtimestamp, cast(:query as jsonb), :saksbehandlerId)
                """.trimIndent(),
                mapOf("query" to mapper.writeValueAsString(gammelQuery), "saksbehandlerId" to saksbehandlerId),
            ))!!
            køId to søkId
        }
        val jobb = PersonbeskyttelseFiltermigrering(txManager)

        jobb.kjør()
        jobb.kjør()

        val forventet = OppgaveQuery(listOf(gammeltFilter.copy(operator = NOT_IN, verdi = listOf("KODE7", "EGEN_ANSATT"))))
        txManager.transaction { tx ->
            listOf("oppgaveko_v3" to køId, "lagret_sok" to søkId).forEach { (tabell, id) ->
                val (versjon, query) = tx.run(queryOf("SELECT versjon, query FROM $tabell WHERE id = :id", mapOf("id" to id))
                    .map { it.long("versjon") to mapper.readValue(it.string("query"), OppgaveQuery::class.java) }.asSingle)!!
                assertEquals(2L, versjon)
                assertEquals(forventet, query)
            }
        }
    }
}
