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

    fun hentAlleEventer(eksternId: String): List<EventLagret> {
        val eventer = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select *
                    from eventlager bpep 
                    where ekstern_id = :ekstern_id
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    rowTilEvent(row)
                }.asList
            )
        }

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

    fun hent(eksternId: String, eventNr: Int): EventLagret? {
        return hentAlleEventer(eksternId).getOrNull(eventNr)
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

    fun hentAlleDirtyEventer(eksternId: String, fagsystem: Fagsystem): List<EventLagret> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select *
                    from eventlager
                    where ekstern_id = :ekstern_id
                    and FAGSYSTEM = :fagsystem
                    and dirty = true
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
