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

    fun lagre(event: String, fagsystem: Fagsystem, tx: TransactionalSession): EventLagret? {
        val tree = LosObjectMapper.instance.readTree(event)
        val eksternId = tree.findValue("eksternId").asText()
        val eksternVersjon = tree.findValue("eventTid").asText()
        val id = tx.run(
            queryOf(
                """
                            insert into eventlager(fagsystem, ekstern_id, ekstern_versjon, "data", dirty) 
                            values (
                            :fagsystem,
                            :ekstern_id,
                            :ekstern_versjon,
                            :data :: jsonb,
                            true)
                            on conflict do nothing
                         """,
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "ekstern_id" to eksternId,
                    "ekstern_versjon" to eksternVersjon,
                    "data" to event
                )
            ).asUpdateAndReturnGeneratedKey
        )


        if (id == null) {
            return hent(fagsystem, eksternId, eksternVersjon, tx)
        }

        return EventLagret(
            id!!,
            fagsystem,
            eksternId,
            eksternVersjon,
            eventJson = event,
            LocalDateTime.now()
        )
    }

    fun hentAlleEventerMedLås(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession): List<EventLagret> {
        val eventer = tx.run(
            queryOf(
                """
                    select *
                    from eventlager bpep 
                    where ekstern_id = :ekstern_id
                    and fagsystem = :fagsystem
                    for update
                """.trimIndent(),
                mapOf(
                    "ekstern_id" to eksternId,
                    "fagsystem" to fagsystem.kode
                )
            ).map { row ->
                rowTilEvent(row)
            }.asList
        )

        return eventer.sortedBy { LocalDateTime.parse(it.eksternVersjon) }
    }

    fun hent(fagsystem: Fagsystem, eksternId: String, eksternVersjon: String, tx: TransactionalSession): EventLagret {
        return tx.run(
            queryOf(
                """
                    select *
                    from eventlager
                    where fagsystem = :fagsystem
                    and ekstern_id = :ekstern_id
                    and ekstern_versjon = :ekstern_versjon
                """.trimIndent(),
                mapOf(
                    "fagsystem" to fagsystem.kode,
                    "ekstern_id" to eksternId,
                    "ekstern_versjon" to eksternVersjon,
                )
            ).map { row -> rowTilEvent(row) }.asSingle
        )!!
    }

    fun hent(id: Long): EventLagret? {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select *
                        from eventlager  
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
                    from eventlager 
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
                    select distinct ekstern_id, fagsystem
                    from eventlager 
                    where dirty = true
                """.trimIndent()
                ).map { row ->
                    EventNøkkel(
                        fagsystem = Fagsystem.fraKode(row.string("fagsystem")),
                        eksternId = row.string("ekstern_id")
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
                    select *
                    from eventlager
                    where ekstern_id = :ekstern_id
                    and FAGSYSTEM = :fagsystem
                    and dirty = true
                    for update
                """.trimIndent(),
                mapOf(
                    "ekstern_id" to eksternId,
                    "fagsystem" to fagsystem.kode
                )
            ).map { row ->
                rowTilEvent(row)
            }.asList
        )
    }

    private fun rowTilEvent(row: Row): EventLagret? {
        return EventLagret(
            id = row.long("id"),
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            eventJson = row.string("data"),
            opprettet = row.localDateTime("opprettet"),
            fagsystem = Fagsystem.fraKode(row.string("fagsystem"))
        )
    }

    fun fjernDirty(eventLagret: EventLagret, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update eventlager
                set dirty = false 
                where id = :id""",
                mapOf(
                    "id" to eventLagret.id
                )
            ).asUpdate
        )
    }

    fun bestillHistorikkvask(fagsystem: Fagsystem) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """insert into eventlager_historikkvask_bestilt(ekstern_id, fagsystem)
                            select distinct ekstern_id, fagsystem
                            from eventlager
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
                """insert into eventlager_historikkvask_bestilt(ekstern_id, fagsystem)
                            select distinct ekstern_id, fagsystem
                            from eventlager
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
                        """delete from eventlager_historikkvask_bestilt
                            where fagsystem = :fagsystem
                            and ekstern_id = :ekstern_id""".trimMargin(),
                        mapOf(
                            "fagsystem" to fagsystem.kode,
                            "ekstern_id" to eksternId
                        )
                    ).asUpdate
                )
            }
        }
    }

    fun hentAntallHistorikkvaskbestillinger() : Long {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select count(*) as antall
                            from eventlager_historikkvask_bestilt
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
                            select *
                            from eventlager_historikkvask_bestilt
                            LIMIT :antall
                        """.trimIndent(),
                        mapOf("antall" to antall)
                    ).map { row ->
                        HistorikkvaskBestilling(
                            fagsystem = Fagsystem.fraKode(row.string("fagsystem")),
                            eksternId = row.string("ekstern_id")
                        )
                    }.asList
                )
            }
        }
    }
}
