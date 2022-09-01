package no.nav.k9.domene.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.domene.modell.K9SakModell
import no.nav.k9.tjenester.innsikt.Databasekall
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource


class BehandlingProsessEventK9Repository(private val dataSource: DataSource) {

    fun hent(uuid: UUID): K9SakModell {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from behandling_prosess_events_k9 where id = :id",
                    mapOf("id" to uuid.toString())
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        if (json.isNullOrEmpty()) {
            return K9SakModell(mutableListOf())
        }
        val modell = objectMapper().readValue(json, K9SakModell::class.java)

        return K9SakModell(  modell.eventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun lagre(uuid: UUID, f: (K9SakModell?) -> K9SakModell): K9SakModell {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        var sortertModell = K9SakModell(mutableListOf())
        using(sessionOf(dataSource)) { it ->
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from behandling_prosess_events_k9 where id = :id for update",
                        mapOf("id" to uuid.toString())
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )

                val modell = if (!run.isNullOrEmpty()) {
                    val modell = objectMapper().readValue(run, K9SakModell::class.java)
                    f(modell.copy(eventer = modell.eventer.sortedBy { it.eventTid }.toMutableList()))
                } else {
                    f(null)
                }
                sortertModell = modell.copy(eventer = (modell.eventer.toSet().toList().sortedBy { it.eventTid }.toMutableList()))
                val json = objectMapper().writeValueAsString(sortertModell)
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_k9 as k (id, data)
                    values (:id, :data :: jsonb)
                    on conflict (id) do update
                    set data = :data :: jsonb
                 """, mapOf("id" to uuid.toString(), "data" to json)
                    ).asUpdate
                )
            }
        }
        return sortertModell
    }

    fun hentAlleEventerIder(
    ): List<UUID> {

        val ider = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_k9",
                        mapOf()
                    )
                        .map { row ->
                            UUID.fromString(row.string("id"))
                   }.asList
                )
            }

        }
        return ider
    }
}
