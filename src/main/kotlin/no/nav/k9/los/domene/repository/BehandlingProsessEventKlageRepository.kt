package no.nav.k9.los.domene.repository

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.aksjonspunktbehandling.objectMapper
import no.nav.k9.los.domene.modell.K9KlageModell
import no.nav.k9.los.tjenester.innsikt.Databasekall
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource


class BehandlingProsessEventKlageRepository(private val dataSource: DataSource) {

    fun hent(uuid: UUID): K9KlageModell {
            val json: String? = using(sessionOf(dataSource)) {
                it.run(
                    queryOf(
                        "select data from behandling_prosess_events_klage where id = :id",
                        mapOf("id" to uuid.toString())
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )
            }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        if (json.isNullOrEmpty()) {
            return K9KlageModell(mutableListOf())
        }
        val modell = objectMapper().readValue(json, K9KlageModell::class.java)

        return K9KlageModell(modell.eventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun hentMedLås(tx: TransactionalSession, uuid: UUID): K9KlageModell {
        val json: String? = tx.run(
            queryOf(
                "select data from behandling_prosess_events_klage where id = :id for update",
                mapOf("id" to uuid.toString())
            )
                .map { row ->
                    row.string("data")
                }.asSingle
        )

        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        if (json.isNullOrEmpty()) {
            return K9KlageModell(mutableListOf())
        }
        val modell = objectMapper().readValue(json, K9KlageModell::class.java)

        return K9KlageModell(modell.eventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun fjernDirty(uuid: UUID, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update behandling_prosess_events_klage set dirty = false where id = :id""",
                mapOf("id" to uuid.toString())
            ).asUpdate
        )
    }

    fun lagre(uuid: UUID, f: (K9KlageModell?) -> K9KlageModell): K9KlageModell {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        var sortertModell = K9KlageModell(mutableListOf())
        using(sessionOf(dataSource)) { it ->
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from behandling_prosess_events_klage where id = :id for update",
                        mapOf("id" to uuid.toString())
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )

                val modell = if (!run.isNullOrEmpty()) {
                    val modell = objectMapper().readValue(run, K9KlageModell::class.java)
                    f(modell.copy(eventer = modell.eventer.sortedBy { it.eventTid }.toMutableList()))
                } else {
                    f(null)
                }
                sortertModell =
                    modell.copy(eventer = (modell.eventer.toSet().toList().sortedBy { it.eventTid }.toMutableList()))
                val json = objectMapper().writeValueAsString(sortertModell)
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_klage as k (id, data, dirty)
                    values (:id, :data :: jsonb, true)
                    on conflict (id) do update
                    set data = :data :: jsonb, dirty = true
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
                        "select id from behandling_prosess_events_klage",
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

    fun hentAlleDirtyEventIder(): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_klage where dirty = true",
                        mapOf()
                    ).map { row ->
                        UUID.fromString(row.string("id"))
                    }.asList
                )
            }
        }
    }

    fun nullstillHistorikkvask() {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """delete * from behandling_prosess_events_klage_historikkvask_ferdig"""
                    ).asUpdate
                )
            }
        }
    }

    fun hentAlleEventIderUtenVasketHistorikk(): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select * 
                            from behandling_prosess_events_klage e
                            where not exists (select * from behandling_prosess_events_klage_historikkvask_ferdig hv where hv.id = e.id)
                             """.trimMargin(),
                        mapOf()
                    ).map { row ->
                        UUID.fromString(row.string("id"))
                    }.asList
                )
            }
        }
    }

    fun markerVasketHistorikk(uuid: UUID, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """insert into behandling_prosess_events_klage_historikkvask_ferdig(id) values (:uuid)""",
                mapOf("uuid" to uuid.toString())
            ).asUpdate
        )
    }

    fun lagreNy(uuid: UUID, modell: K9KlageModell) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_klage as k (id, data)
                    values (:id, :data :: jsonb)
                    on conflict (id) do update
                    set data = :data :: jsonb
                 """, mapOf("id" to uuid.toString(), "data" to objectMapper().writeValueAsString(modell))
                    ).asUpdate
                )
            }
        }
    }
}
