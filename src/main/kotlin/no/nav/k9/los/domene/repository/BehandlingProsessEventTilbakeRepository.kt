package no.nav.k9.los.domene.repository

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.modell.K9TilbakeModell
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.utils.LosObjectMapper
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource


class BehandlingProsessEventTilbakeRepository(private val dataSource: DataSource) {
    fun hent(uuid: UUID): K9TilbakeModell {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from behandling_prosess_events_tilbake where id = :id",
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
            return K9TilbakeModell(mutableListOf())
        }
        val modell = LosObjectMapper.instance.readValue(json, K9TilbakeModell::class.java)
        val unikeEventer = BehandlingProsessEventK9TilbakeDuplikatUtil.fjernDuplikater(modell.eventer)
        return K9TilbakeModell(unikeEventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun lagre(
        event: BehandlingProsessEventTilbakeDto
    ): K9TilbakeModell {
        val json = LosObjectMapper.instance.writeValueAsString(event)

        val id = event.eksternId.toString()
        val out = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_tilbake as k (id, data)
                    values (:id, :dataInitial :: jsonb)
                    on conflict (id) do update
                    set data = jsonb_set(k.data, '{eventer,999999}', :data :: jsonb, true), dirty = true
                 """, mapOf("id" to id, "dataInitial" to "{\"eventer\": [$json]}", "data" to json)
                    ).asUpdate
                )
                tx.run(
                    queryOf(
                        "select data from behandling_prosess_events_tilbake where id = :id",
                        mapOf("id" to id)
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )
            }

        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        val modell = LosObjectMapper.instance.readValue(out!!, K9TilbakeModell::class.java)
        val unikeEventer = BehandlingProsessEventK9TilbakeDuplikatUtil.fjernDuplikater(modell.eventer)
        return K9TilbakeModell(unikeEventer.sortedBy { it.eventTid }.toMutableList())
    }


    fun hentAntallEventIderUtenVasketHistorikk(): Long {
        return using(sessionOf(dataSource)) {
                session -> session.transaction { tx ->
            tx.run(
                queryOf(
                    """
                            select count(*) as antall
                            from behandling_prosess_events_tilbake e
                            where not exists (select * from behandling_prosess_events_tilbake_historikkvask_ferdig hv where hv.id = e.id)
                             """.trimMargin(),
                ).map { it.long("antall") }.asSingle
            )!!
        }
        }
    }

    fun hentAlleEventIderUtenVasketHistorikk(antall: Int = 10000): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select * 
                            from behandling_prosess_events_tilbake e
                            where not exists (select * from behandling_prosess_events_tilbake_historikkvask_ferdig hv where hv.id = e.id)
                            LIMIT :antall
                             """.trimMargin(),
                        mapOf("antall" to antall)
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
                """insert into behandling_prosess_events_tilbake_historikkvask_ferdig(id) values (:uuid)""",
                mapOf("uuid" to uuid.toString())
            ).asUpdate
        )
    }

    fun nullstillHistorikkvask() {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """delete from behandling_prosess_events_tilbake_historikkvask_ferdig"""
                    ).asUpdate
                )
            }
        }
    }

    fun hentMedLås(tx: TransactionalSession, uuid: UUID): K9TilbakeModell {
        val json: String? = tx.run(
            queryOf(
                "select data from behandling_prosess_events_tilbake where id = :id for update",
                mapOf("id" to uuid.toString())
            )
                .map { row ->
                    row.string("data")
                }.asSingle
        )

        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        if (json.isNullOrEmpty()) {
            return K9TilbakeModell(mutableListOf())
        }
        val modell = LosObjectMapper.instance.readValue(json, K9TilbakeModell::class.java)
        val unikeEventer = BehandlingProsessEventK9TilbakeDuplikatUtil.fjernDuplikater(modell.eventer)
        return K9TilbakeModell(unikeEventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun hentAlleDirtyEventIder(): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_tilbake where dirty = true",
                        mapOf()
                    ).map { row ->
                        UUID.fromString(row.string("id"))
                    }.asList
                )
            }
        }
    }

    fun fjernDirty(uuid: UUID, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update behandling_prosess_events_tilbake set dirty = false where id = :id""",
                mapOf("id" to uuid.toString())
            ).asUpdate
        )
    }


}
