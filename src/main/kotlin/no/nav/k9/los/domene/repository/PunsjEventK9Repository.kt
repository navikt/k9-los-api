package no.nav.k9.los.domene.repository

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.modell.K9PunsjModell
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.innsikt.Mapping
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource


class PunsjEventK9Repository(private val dataSource: DataSource) {
    private val log: Logger = LoggerFactory.getLogger(PunsjEventK9Repository::class.java)

    fun hent(uuid: UUID): K9PunsjModell {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from behandling_prosess_events_k9_punsj where id = :id",
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
            return K9PunsjModell(emptyList())
        }
        return try {
            val modell = LosObjectMapper.instance.readValue(json, K9PunsjModell::class.java)
            K9PunsjModell(modell.eventer.sortedBy { it.eventTid })
        } catch (e: Exception) {
            log.error("", e)
            K9PunsjModell(emptyList())
        }
    }

    fun hentMedLås(tx: TransactionalSession, uuid: UUID): K9PunsjModell {
        val json: String? =tx.run(
                queryOf(
                    "select data from behandling_prosess_events_k9_punsj where id = :id for update",
                    mapOf("id" to uuid.toString())
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        if (json.isNullOrEmpty()) {
            return K9PunsjModell(emptyList())
        }
        return try {
            val modell = LosObjectMapper.instance.readValue(json, K9PunsjModell::class.java)
            K9PunsjModell(modell.eventer.sortedBy { it.eventTid })
        } catch (e: Exception) {
            log.error("", e)
            K9PunsjModell(emptyList())
        }
    }

    fun fjernDirty(uuid: UUID, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update behandling_prosess_events_k9_punsj set dirty = false where id = :id""",
                mapOf("id" to uuid.toString())
            ).asUpdate
        )
    }

    fun settDirty(uuid: UUID, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update behandling_prosess_events_k9_punsj set dirty = true where id = :id""",
                mapOf("id" to uuid.toString())
            ).asUpdate
        )
    }

    fun lagre(
        event: PunsjEventDto
    ): K9PunsjModell {
        val json = LosObjectMapper.instance.writeValueAsString(event)

        val id = event.eksternId.toString()
        val out = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_k9_punsj as k (id, data)
                    values (:id, :dataInitial :: jsonb)
                    on conflict (id) do update
                    set data = jsonb_set(k.data, '{eventer,999999}', :data :: jsonb, true)
                 """, mapOf("id" to id, "dataInitial" to "{\"eventer\": [$json]}", "data" to json)
                    ).asUpdate
                )
                tx.run(
                    queryOf(
                        "select data from behandling_prosess_events_k9_punsj where id = :id",
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
        val modell = LosObjectMapper.instance.readValue(out!!, K9PunsjModell::class.java)
        return modell.copy(eventer = modell.eventer.sortedBy { it.eventTid })
    }

    ///
    fun hentAlleEventerIder(
    ): List<UUID> {

        val ider = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_k9_punsj",
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
                        "select id from behandling_prosess_events_k9_punsj where dirty = true",
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
                        """delete from behandling_prosess_events_k9_punsj_historikkvask_ferdig"""
                    ).asUpdate
                )
            }
        }
    }

    fun hentAntallEventIderUtenVasketHistorikk(): Long {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select count(*) as antall
                            from behandling_prosess_events_k9_punsj e
                            where not exists (select * from behandling_prosess_events_k9_punsj_historikkvask_ferdig hv where hv.id = e.id)
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
                            from behandling_prosess_events_k9_punsj e
                            where not exists (select * from behandling_prosess_events_k9_punsj_historikkvask_ferdig hv where hv.id = e.id)
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
                """insert into behandling_prosess_events_k9_punsj_historikkvask_ferdig(id) values (:uuid)""",
                mapOf("uuid" to uuid.toString())
            ).asUpdate
        )
    }


}
