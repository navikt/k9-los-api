package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.jetbrains.annotations.VisibleForTesting
import java.time.LocalDateTime
import javax.sql.DataSource


class EventRepository(
    private val dataSource: DataSource,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(EventRepository::class.java)

    fun upsertOgLåsEventnøkkel(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession): Long {
        val id = tx.run(
            queryOf(
                """
                    select id
                    from event_nokkel
                    where fagsystem = :fagsystem
                    and ekstern_id = :eksternId
                     for update
                """.trimIndent(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "eksternId" to eksternId
                )
            ).map { row ->
                row.long("id")
            }.asSingle
        )

        if (id != null) {
            return id
        } else {
            return tx.run(
                queryOf(
                    """
                    insert into event_nokkel (ekstern_id, fagsystem)
                    values(:eksternId, :fagsystem)
                    returning id
                """.trimIndent(),
                    mapOf(
                        "eksternId" to eksternId,
                        "fagsystem" to fagsystem.kode
                    )
                ).asUpdateAndReturnGeneratedKey
            )!!
        }
    }

    fun hentOgLåsEventnøkkel(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession): Long {
        return tx.run(
            queryOf(
                """
                    select id
                    from event_nokkel
                    where fagsystem = :fagsystem
                    and ekstern_id = :eksternId
                    for update
                """.trimIndent(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "eksternId" to eksternId
                )
            ).map { row ->
                row.long("id")
            }.asSingle
        )!!
    }

    fun lagre(
        fagsystem: Fagsystem,
        eksternId: String,
        eksternVersjon: String,
        event: String,
        tx: TransactionalSession
    ): EventLagret? {
        val eventnøkkelId = upsertOgLåsEventnøkkel(fagsystem, eksternId, tx)

        tx.run(
            queryOf(
                """
                        insert into event(event_nokkel_id, ekstern_versjon, "data", dirty) 
                        values (
                        :event_nokkel_id,
                        :ekstern_versjon,
                        :data :: jsonb,
                        true)
                        on conflict do nothing 
                     """, //TODO: on conflict update data? jfr vaskeevent
                mapOf(
                    "event_nokkel_id" to eventnøkkelId,
                    "ekstern_versjon" to eksternVersjon,
                    "data" to event
                )
            ).asUpdate
        )

        return hent(fagsystem, eksternId, eksternVersjon, tx)
    }

    fun hentAlleEventer(fagsystem: Fagsystem, eksternId: String): List<EventLagret> {
        return using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                hentAlleEventer(fagsystem, eksternId, tx)
            }
        }
    }

    fun hentAlleEventer(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession): List<EventLagret> {
        val eventId = hentOgLåsEventnøkkel(fagsystem, eksternId, tx)
        val eventer = tx.run(
            queryOf(
                """
                    select e.* 
                    from event e 
                    where event_nokkel_id = :nokkelId
                    order by ekstern_versjon :: timestamp
                """.trimIndent(),
                mapOf(
                    "nokkelId" to eventId,
                )
            ).map { row ->
                rowTilEvent(row, eksternId, fagsystem)
            }.asList
        )

        return eventer.sortedBy { LocalDateTime.parse(it.eksternVersjon) }
    }


    fun hentAlleEventerMedLås(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession): List<EventLagret> {
        val eventId = hentOgLåsEventnøkkel(fagsystem, eksternId, tx)
        val eventer = tx.run(
            queryOf(
                """
                    select e.* 
                    from event e 
                    where event_nokkel_id = :nokkelId
                """.trimIndent(),
                mapOf(
                    "nokkelId" to eventId,
                )
            ).map { row ->
                rowTilEvent(row, eksternId, fagsystem)
            }.asList
        )

        return eventer.sortedBy { LocalDateTime.parse(it.eksternVersjon) }
    }

    @VisibleForTesting
    fun hent(fagsystem: Fagsystem, eksternId: String, eksternVersjon: String, tx: TransactionalSession): EventLagret {
        return tx.run(
            queryOf(
                """
                    select e.*
                    from event e
                        join event_nokkel en on e.event_nokkel_id = en.id
                    where fagsystem = :fagsystem
                    and ekstern_id = :ekstern_id
                    and ekstern_versjon = :ekstern_versjon
                """.trimIndent(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "ekstern_id" to eksternId,
                    "ekstern_versjon" to eksternVersjon,
                )
            ).map { row -> rowTilEvent(row, eksternId, fagsystem) }.asSingle
        )!!
    }

    fun hentAlleEksternIderMedDirtyEventer(): List<EventNøkkel> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select distinct en.*
                    from event e
                        join event_nokkel en on e.event_nokkel_id = en.id
                    where e.dirty = true
                """.trimIndent()
                ).map { row ->
                    EventNøkkel(
                        eksternId = row.string("ekstern_id"),
                        fagsystem = Fagsystem.fraKode(row.string("fagsystem"))
                    )
                }.asList
            )
        }
    }

    fun hentAlleDirtyEventerNummerertMedLås(
        fagsystem: Fagsystem,
        eksternId: String,
        tx: TransactionalSession
    ): List<Pair<Int, EventLagret>> {
        return tx.run(
            queryOf(
                """
                with oppgaveversjoner as (
                    select e.event_nokkel_id, en.fagsystem, en.ekstern_id, e.ekstern_versjon, e."data", row_number() over (order by ekstern_versjon :: timestamp) -1 as nummer, e.dirty, e.opprettet
                    from "event" e 
                        join event_nokkel en on e.event_nokkel_id = en.id 
                    where en.ekstern_id = :ekstern_id
                    and en.fagsystem = :fagsystem
                )
                select *
                from oppgaveversjoner
                where dirty = true
                for update
                """.trimIndent(), //select for update ikke lov med distinct, derfor CTE
                mapOf(
                    "ekstern_id" to eksternId,
                    "fagsystem" to fagsystem.kode
                )
            ).map { row ->
                Pair(row.int("nummer"), rowTilEvent(row, eksternId, fagsystem))
            }.asList
        ).sortedBy { LocalDateTime.parse(it.second.eksternVersjon) }
    }

    private fun rowTilEvent(row: Row): EventLagret? {
        return EventLagret.create(
            nøkkelId = row.long("id"),
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            eventJson = row.string("data"),
            opprettet = row.localDateTime("opprettet"),
            fagsystem = Fagsystem.fraKode(row.string("fagsystem")),
            dirty = row.boolean("dirty")
        )
    }

    private fun rowTilEvent(row: Row, eksternId: String, fagsystem: Fagsystem): EventLagret {
        return EventLagret.create(
            nøkkelId = row.long("event_nokkel_id"),
            eksternId = eksternId,
            eksternVersjon = row.string("ekstern_versjon"),
            eventJson = row.string("data"),
            opprettet = row.localDateTime("opprettet"),
            fagsystem = fagsystem,
            dirty = row.boolean("dirty")
        )
    }

    fun fjernDirty(eventLagret: EventLagret, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update event
                set dirty = false 
                where event_nokkel_id = :id
                and ekstern_versjon = :ekstern_versjon""",
                mapOf(
                    "id" to eventLagret.nøkkelId,
                    "ekstern_versjon" to eventLagret.eksternVersjon
                )
            ).asUpdate
        )
    }

    fun settDirty(eventnøkkel: EventNøkkel, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                update event
                set dirty = true 
                where event_nokkel_id = (select id 
                                        from event_nokkel 
                                        where fagsystem = :fagsystem
                                        and ekstern_id = :ekstern_id)
            """.trimMargin(),
                mapOf(
                    "fagsystem" to eventnøkkel.fagsystem.kode,
                    "ekstern_id" to eventnøkkel.eksternId,
                )
            ).asUpdate
        )
    }

    fun bestillHistorikkvask(fagsystem: Fagsystem) {
        log.info("Bestiller historikkvask for alle i fagsystem ${fagsystem.kode}")
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """insert into event_historikkvask_bestilt(event_nokkel_id)
                            select id
                            from event_nokkel
                            where fagsystem = :fagsystem
                            on conflict do nothing
                        """.trimMargin(),
                        mapOf(
                            "fagsystem" to fagsystem.kode,
                        )
                    ).asUpdate
                )
            }
        }
    }

    fun bestillHistorikkvask(fagsystem: Fagsystem, eksternId: String) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                bestillHistorikkvask(fagsystem, eksternId, tx)
            }
        }
    }

    fun bestillHistorikkvask(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """insert into event_historikkvask_bestilt(event_nokkel_id)
                            select id
                            from event_nokkel
                            where fagsystem = :fagsystem
                            and ekstern_id = :ekstern_id
                            on conflict do nothing
                        """.trimMargin(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "ekstern_id" to eksternId
                )
            ).asUpdate
        )
    }

    fun settHistorikkvaskFerdig(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """delete from event_historikkvask_bestilt
                            where event_nokkel_id = (select id
                                        from event_nokkel
                                        where fagsystem = :fagsystem
                                         and ekstern_id = :ekstern_id)
                                    """.trimMargin(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "ekstern_id" to eksternId
                )
            ).asUpdate
        )
    }

    fun hentAntallHistorikkvaskbestillinger(): Long {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select count(*) as antall
                            from event_historikkvask_bestilt
                        """.trimIndent()
                    ).map { it.long("antall") }.asSingle
                )!!
            }
        }
    }

    fun hentAlleHistorikkvaskbestillinger(antall: Int = 10000): List<HistorikkvaskBestilling> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select en.*
                            from event_historikkvask_bestilt hb
                                join event_nokkel en on hb.event_nokkel_id = en.id
                            LIMIT :antall
                        """.trimIndent(),
                        mapOf("antall" to antall)
                    ).map { row ->
                        HistorikkvaskBestilling(
                            row.long("id"),
                            row.string("ekstern_id"),
                            Fagsystem.fraKode(row.string("fagsystem"))
                        )
                    }.asList
                )
            }
        }
    }
}
