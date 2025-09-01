package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.feilhandtering.DuplikatDataException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.postgresql.util.PSQLException
import javax.sql.DataSource


class EventRepository(
    private val dataSource: DataSource,
) {

    fun lagre(event: String, fagsystem: Fagsystem, internVersjon: Int, tx: TransactionalSession): EventLagret? {
        val tree = LosObjectMapper.instance.readTree(event)
        val eksternId = tree.findValue("eksternId").asText()
        val eksternVersjon = tree.findValue("eventTid").asText()
        return try {
            tx.run(
                queryOf(
                    """
                            insert into eventlager(fagsystem, ekstern_id, ekstern_versjon, intern_versjon, "data", dirty) 
                            values (
                            :fagsystem,
                            :ekstern_id,
                            :ekstern_versjon,
                            :intern_versjon,
                            :data :: jsonb,
                            true)
                            on conflict do nothing
                            returning id, fagsystem, ekstern_id, ekstern_versjon, intern_versjon, data, opprettet
                         """,
                    mapOf(
                        "fagsystem" to fagsystem.kode,
                        "ekstern_id" to eksternId,
                        "ekstern_versjon" to eksternVersjon,
                        "intern_versjon" to internVersjon,
                        "data" to event
                    )
                ).map { row ->
                    rowTilEvent(row)
                }.asSingle
            )
        } catch (e: PSQLException) {
            if (e.sqlState == "23505") {
                throw DuplikatDataException("Event med fagsystem: ${fagsystem}, eksternId: ${eksternId} og eksternVersjon: ${eksternVersjon} finnes allerede!")
            } else {
                throw e
            }
        }
    }

    fun lagre(event: String, fagsystem: Fagsystem, tx: TransactionalSession): EventLagret? {
        val tree = LosObjectMapper.instance.readTree(event)
        val eksternId = tree.findValue("eksternId").asText()
        val eksternVersjon = tree.findValue("eventTid").asText()
        return try {
            tx.run(
                queryOf(
                    """
                            insert into eventlager(fagsystem, ekstern_id, ekstern_versjon, intern_versjon, "data", dirty) 
                            values (
                            :fagsystem,
                            :ekstern_id,
                            :ekstern_versjon,
                            (select coalesce(max(intern_versjon)+1, 0) from eventlager where ekstern_id = :ekstern_id),
                            :data :: jsonb,
                            true)
                            returning id, fagsystem, ekstern_id, ekstern_versjon, intern_versjon, data, opprettet
                         """,
                    mapOf(
                        "fagsystem" to fagsystem.kode,
                        "ekstern_id" to eksternId,
                        "ekstern_versjon" to eksternVersjon,
                        "data" to event
                    )
                ).map { row ->
                    rowTilEvent(row)
                }.asSingle
            )
        } catch (e: PSQLException) {
            if (e.sqlState == "23505") {
                throw DuplikatDataException("Punsjevent med eksternId: ${eksternId} og eksternVersjon: ${eksternVersjon} finnes allerede!")
            } else {
                throw e
            }
        }
    }

    fun hent(eksternId: String, eventNr: Long): EventLagret? {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select *
                        from eventlager  
                        where ekstern_id = :ekstern_id
                        and intern_versjon = :eventnr
                    """.trimIndent(),
                    mapOf(
                        "ekstern_id" to eksternId,
                        "eventnr" to eventNr
                    )
                ).map { row ->
                    rowTilEvent(row)
                }.asSingle
            )
        }
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

    fun hentAlleEventer(eksternId: String): List<EventLagret> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select *
                    from eventlager bpep 
                    where ekstern_id = :ekstern_id
                    order by intern_versjon ASC
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    rowTilEvent(row)
                }.asList
            )
        }
    }

    fun hentAlleDirtyEventer(eksternId: String): List<EventLagret> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select *
                    from eventlager 
                    where ekstern_id = :ekstern_id
                    and dirty = true
                    order by intern_versjon ASC
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    rowTilEvent(row)
                }.asList
            )
        }
    }

    private fun rowTilEvent(row: Row): EventLagret? {
        return EventLagret(
            id = row.long("id"),
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            eventNrForOppgave = row.int("intern_versjon"),
            eventJson = row.string("data"),
            opprettet = row.localDateTime("opprettet"),
            fagsystem = Fagsystem.fraKode(row.string("fagsystem"))
        )
    }

    fun fjernDirty(eksternId: String, eventNr: Long, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update eventlager
                set dirty = false 
                where ekstern_id = :ekstern_id
                and intern_versjon = :eventnr""",
                mapOf(
                    "ekstern_id" to eksternId,
                    "eventnr" to eventNr
                )
            ).asUpdate
        )
    }

    fun nullstillHistorikkvask() {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """delete from eventlager_historikkvask_ferdig"""
                    ).asUpdate
                )
            }
        }
    }

    fun hentAlleEventIderUtenVasketHistorikk(): List<Long> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select id
                            from eventlager e
                            where not exists (select * from eventlager_historikkvask_ferdig hv where hv.id = e.id)
                             """.trimMargin(),
                        mapOf()
                    ).map { row ->
                        row.long("id")
                    }.asList
                )
            }
        }
    }

    fun markerVasketHistorikk(eventLagret: EventLagret) {
        using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """insert into eventlager_historikkvask_ferdig(id) values (:id)""",
                    mapOf("id" to eventLagret.id)
                ).asUpdate
            )
        }
    }
}
