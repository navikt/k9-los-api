package no.nav.k9.los.domene.repository

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.domene.modell.K9SakModell
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.utils.LosObjectMapper
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
        if (json.isNullOrEmpty()) {
            return K9SakModell(mutableListOf())
        }
        val modell = LosObjectMapper.instance.readValue(json, K9SakModell::class.java)

        val unikeEventer = BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(modell.eventer)
        return K9SakModell(unikeEventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun hentMedLås(tx: TransactionalSession, uuid: UUID): K9SakModell {
        val json: String? = tx.run(
            queryOf(
                "select data from behandling_prosess_events_k9 where id = :id for update",
                mapOf("id" to uuid.toString())
            )
                .map { row ->
                    row.string("data")
                }.asSingle
        )

        if (json.isNullOrEmpty()) {
            return K9SakModell(mutableListOf())
        }
        val modell = LosObjectMapper.instance.readValue(json, K9SakModell::class.java)
        val unikeEventer = BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(modell.eventer)
        return K9SakModell(unikeEventer.sortedBy { it.eventTid }.toMutableList())
    }



    fun fjernDirty(uuid: UUID, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update behandling_prosess_events_k9 set dirty = false where id = :id""",
                mapOf("id" to uuid.toString())
            ).asUpdate
        )
    }

    fun lagre(uuid: UUID, f: (K9SakModell?) -> K9SakModell): K9SakModell {
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
                    val modell = LosObjectMapper.instance.readValue(run, K9SakModell::class.java)
                    f(modell.copy(eventer = BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(modell.eventer).sortedBy { it.eventTid }.toMutableList()))
                } else {
                    f(null)
                }
                sortertModell =
                    modell.copy(eventer = (BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(modell.eventer).sortedBy { it.eventTid }.toMutableList()))
                val json = LosObjectMapper.instance.writeValueAsString(sortertModell)
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_k9 as k (id, data, dirty)
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

    fun hentAlleDirtyEventIder(): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_k9 where dirty = true",
                        mapOf()
                    ).map { row ->
                        UUID.fromString(row.string("id"))
                    }.asList
                )
            }
        }
    }

    fun hentAntallEventIderUtenVasketHistorikk(): Long {
        return using(sessionOf(dataSource)) {
            session -> session.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select count(*) as antall
                            from behandling_prosess_events_k9 e
                            where not exists (select * from behandling_prosess_events_k9_historikkvask_ferdig hv where hv.id = e.id)
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
                            from behandling_prosess_events_k9 e
                            where not exists (select * from behandling_prosess_events_k9_historikkvask_ferdig hv where hv.id = e.id)
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
                """insert into behandling_prosess_events_k9_historikkvask_ferdig(id) values (:uuid)""",
                mapOf("uuid" to uuid.toString())
            ).asUpdate
        )
    }

    fun nullstillHistorikkvask() {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """delete from behandling_prosess_events_k9_historikkvask_ferdig"""
                    ).asUpdate
                )
            }
        }
    }

}
