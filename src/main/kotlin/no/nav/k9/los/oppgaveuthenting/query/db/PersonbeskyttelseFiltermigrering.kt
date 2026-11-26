package no.nav.k9.los.oppgaveuthenting.query.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.Personbeskyttelsefilter
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

internal class PersonbeskyttelseFiltermigrering(private val transactionalManager: TransactionalManager) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun kjør() {
        val (antall, tidsbruk) = measureTimedValue {
            transactionalManager.transaction { tx ->
                migrer(tx, "oppgaveko_v3", "endret_tidspunkt", ":query") to
                    migrer(tx, "lagret_sok", "sist_endret", "cast(:query as jsonb)")
            }
        }
        log.info("Migrerte personbeskyttelsesfiltre: køer={}, lagredeSøk={}, tidsbruk={}", antall.first, antall.second, tidsbruk)
    }

    private fun migrer(tx: TransactionalSession, tabell: String, tidsstempel: String, queryVerdi: String): Int {
        val rader = tx.run(queryOf(
            """
            SELECT id, query FROM $tabell
            WHERE cast(query as text) LIKE '%KODE7_ELLER_EGEN_ANSATT%'
               OR cast(query as text) LIKE '%UGRADERT%'
            ORDER BY id FOR UPDATE
            """.trimIndent()
        ).map { it.long("id") to it.string("query") }.asList)

        val mapper = LosObjectMapper.instance
        return rader.count { (id, json) ->
            val gammel = mapper.readValue(json, OppgaveQuery::class.java)
            val ny = Personbeskyttelsefilter.migrer(gammel)
            if (ny == gammel) return@count false

            check(tx.run(queryOf(
                "UPDATE $tabell SET query = $queryVerdi, versjon = versjon + 1, $tidsstempel = localtimestamp WHERE id = :id",
                mapOf("id" to id, "query" to mapper.writeValueAsString(ny)),
            ).asUpdate) == 1)
            true
        }
    }
}
