package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.time.LocalDateTime
import javax.sql.DataSource


class EventRepository(
    private val dataSource: DataSource,
) {

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

    fun lagre(fagsystem: Fagsystem, event: String, tx: TransactionalSession): EventLagret? {
        val tree = LosObjectMapper.instance.readTree(event)
        val eksternId = tree.findValue("eksternId").asText()
        val eksternVersjon = tree.findValue("eventTid").asText()
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

    fun endreEvent(eventNøkkel: EventNøkkel, event: String, tx: TransactionalSession): EventLagret? {
        val tree = LosObjectMapper.instance.readTree(event)
        val eksternVersjon = tree.findValue("eventTid").asText()

        tx.run(
            queryOf(
                """
                        update event set "data" = :data :: jsonb
                        where event_nokkel_id = :event_nokkel_id 
                     """,
                mapOf(
                    "event_nokkel_id" to eventNøkkel.nøkkelId,
                    "data" to event
                )
            ).asUpdate
        )

        return hent(eventNøkkel.fagsystem, eventNøkkel.eksternId, eksternVersjon, tx)
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

    fun hent(id: Long): EventLagret? { //TODO: slette?
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select *
                        from event  
                        where id = :id
                    """.trimIndent(),
                    mapOf(
                        "id" to id,
                    )
                ).map { row ->
                    rowTilEvent(row)
                }.asSingle
            )
        }
    }

    fun hentAlleEksternIderMedDirtyEventer(fagsystem: Fagsystem): List<String> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select distinct ekstern_id
                    from event 
                    where fagsystem = :fagsystem
                    and dirty = true
                """.trimIndent(),
                    mapOf("fagsystem" to fagsystem.kode)
                ).map { row ->
                    row.string("ekstern_id")
                }.asList
            )
        }
    }

    fun hentAlleEksternIderMedDirtyEventer(): List<EventNøkkel> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select en.*
                    from event e
                        join event_nokkel en on e.event_nokkel_id = en.id
                    where e.dirty = true
                    and en.fagsystem = '${Fagsystem.PUNSJ.kode}'
                """.trimIndent() //TODO: Midlertidig filter på punsj for pilottest
                ).map { row ->
                    EventNøkkel(
                        nøkkelId = row.long("id"),
                        eksternId = row.string("ekstern_id"),
                        fagsystem = Fagsystem.fraKode(row.string("fagsystem"))
                    )
                }.asList
            )
        }
    }

    fun hentAlleDirtyEventerMedLås(
        fagsystem: Fagsystem,
        eksternId: String,
        tx: TransactionalSession
    ): List<EventLagret> {
        return tx.run(
            queryOf(
                """
                    select e.*
                    from event e
                        join event_nokkel en on e.event_nokkel_id = en.id 
                    where en.ekstern_id = :ekstern_id
                    and en.FAGSYSTEM = :fagsystem
                    and e.dirty = true
                    for update
                """.trimIndent(),
                mapOf(
                    "ekstern_id" to eksternId,
                    "fagsystem" to fagsystem.kode
                )
            ).map { row ->
                rowTilEvent(row, eksternId, fagsystem)
            }.asList
        )
    }

    private fun rowTilEvent(row: Row): EventLagret? {
        return EventLagret(
            nøkkelId = row.long("id"),
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            eventJson = row.string("data"),
            opprettet = row.localDateTime("opprettet"),
            fagsystem = Fagsystem.fraKode(row.string("fagsystem"))
        )
    }

    private fun rowTilEvent(row: Row, eksternId: String, fagsystem: Fagsystem): EventLagret? {
        return EventLagret(
            nøkkelId = row.long("event_nokkel_id"),
            eksternId = eksternId,
            eksternVersjon = row.string("ekstern_versjon"),
            eventJson = row.string("data"),
            opprettet = row.localDateTime("opprettet"),
            fagsystem = fagsystem
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

    fun bestillHistorikkvask(fagsystem: Fagsystem) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """insert into event_historikkvask_bestilt(event_nokkel_id)
                            select id
                            from event_nokkel
                            where fagsystem = :fagsystem
                        """.trimMargin(),
                        mapOf(
                            "fagsystem" to fagsystem.kode,
                        )
                    ).asUpdate
                )
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
                        """.trimMargin(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "ekstern_id" to eksternId
                )
            ).asUpdate
        )
    }

    fun settHistorikkvaskFerdig(fagsystem: Fagsystem, eksternId: String) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
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
        }
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
                                where en.fagsystem = '${Fagsystem.PUNSJ.kode}'
                            LIMIT :antall
                        """.trimIndent(), //TODO: Midlertidig filter på punsj for pilottest
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
