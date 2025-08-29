package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.feilhandtering.DuplikatDataException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.postgresql.util.PSQLException
import javax.sql.DataSource


class EventRepositoryPerLinje(
    private val dataSource: DataSource,
    private val transactionalManager: TransactionalManager
) {

    fun lagre(event: PunsjEventDto): EventPerLinjeLagret? {
        val json = LosObjectMapper.instance.writeValueAsString(event)
        return try {
            using(sessionOf(dataSource)) {
                it.run(
                    queryOf(
                        """
                            insert into eventlager_punsj(ekstern_id, ekstern_versjon, eventnr_for_oppgave, "data", dirty) 
                            values (:ekstern_id,
                            :ekstern_versjon,
                            (select coalesce(max(eventnr_for_oppgave)+1, 0) from eventlager_punsj where ekstern_id = :ekstern_id),
                            :data :: jsonb,
                            true)
                            returning id, ekstern_id, ekstern_versjon, eventnr_for_oppgave, data, opprettet
                         """,
                        mapOf(
                            "ekstern_id" to event.eksternId,
                            "ekstern_versjon" to event.eventTid,
                            "data" to json
                        )
                    ).map { row ->
                        EventPerLinjeLagret(
                            id = row.long("id"),
                            eksternId = row.string("ekstern_id"),
                            eksternVersjon = row.string("ekstern_versjon"),
                            eventNrForOppgave = row.int("eventnr_for_oppgave"),
                            eventDto = LosObjectMapper.instance.readValue(row.string("data"), PunsjEventDto::class.java),
                            opprettet = row.localDateTime("opprettet")
                        )
                    }.asSingle
                )
            }
        } catch (e: PSQLException) {
            if (e.sqlState == "23505") {
                throw DuplikatDataException("Punsjevent med eksternId: ${event.eksternId} og eksternVersjon: ${event.eventTid} finnes allerede!")
            } else {
                throw e
            }
        }
    }

    fun hent(eksternId: String, eventNr: Long): EventPerLinjeLagret? {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select *
                        from eventlager_punsj  
                        where ekstern_id = :ekstern_id
                        and eventnr_for_oppgave = :eventnr
                    """.trimIndent(),
                    mapOf(
                        "ekstern_id" to eksternId,
                        "eventnr" to eventNr
                    )
                ).map { row ->
                    EventPerLinjeLagret(
                        id = row.long("id"),
                        eksternId = row.string("ekstern_id"),
                        eksternVersjon = row.string("ekstern_versjon"),
                        eventNrForOppgave = row.int("eventnr_for_oppgave"),
                        eventDto = LosObjectMapper.instance.readValue(row.string("data"), PunsjEventDto::class.java),
                        opprettet = row.localDateTime("opprettet")
                    )
                }.asSingle
            )
        }
    }

    fun hent(id: Long): EventPerLinjeLagret? {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select *
                        from eventlager_punsj  
                        where id = :id
                    """.trimIndent(),
                    mapOf(
                        "id" to id,
                    )
                ).map { row ->
                    EventPerLinjeLagret(
                        id = row.long("id"),
                        eksternId = row.string("ekstern_id"),
                        eksternVersjon = row.string("ekstern_versjon"),
                        eventNrForOppgave = row.int("eventnr_for_oppgave"),
                        eventDto = LosObjectMapper.instance.readValue(row.string("data"), PunsjEventDto::class.java),
                        opprettet = row.localDateTime("opprettet")
                    )
                }.asSingle
            )
        }
    }

    fun hentAlleEventer(eksternId: String): List<EventPerLinjeLagret> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select *
                    from eventlager_punsj bpep 
                    where ekstern_id = :ekstern_id
                    order by eventnr_for_oppgave ASC
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    EventPerLinjeLagret(
                        id = row.long("id"),
                        eksternId = row.string("ekstern_id"),
                        eksternVersjon = row.string("ekstern_versjon"),
                        eventNrForOppgave = row.int("eventnr_for_oppgave"),
                        eventDto = LosObjectMapper.instance.readValue(row.string("data"), PunsjEventDto::class.java),
                        opprettet = row.localDateTime("opprettet")
                    )
                }.asList
            )
        }
    }

    fun hentAlleDirtyEventer(eksternId: String): List<EventPerLinjeLagret> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select *
                    from eventlager_punsj 
                    where ekstern_id = :ekstern_id
                    and dirty = true
                    order by eventnr_for_oppgave ASC
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    EventPerLinjeLagret(
                        id = row.long("id"),
                        eksternId = row.string("ekstern_id"),
                        eksternVersjon = row.string("ekstern_versjon"),
                        eventNrForOppgave = row.int("eventnr_for_oppgave"),
                        eventDto = LosObjectMapper.instance.readValue(row.string("data"), PunsjEventDto::class.java),
                        opprettet = row.localDateTime("opprettet")
                    )
                }.asList
            )
        }
    }

    fun fjernDirty(eksternId: String, eventNr: Long, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """update eventlager_punsj
                set dirty = false 
                where ekstern_id = :ekstern_id
                and eventnr_for_oppgave = :eventnr""",
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
                        """delete from eventlager_punsj_historikkvask_ferdig"""
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
                            from eventlager_punsj e
                            where not exists (select * from eventlager_punsj_historikkvask_ferdig hv where hv.id = e.id)
                             """.trimMargin(),
                        mapOf()
                    ).map { row ->
                        row.long("id")
                    }.asList
                )
            }
        }
    }

    fun markerVasketHistorikk(eventPerLinjeLagret: EventPerLinjeLagret) {
        using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """insert into eventlager_punsj_historikkvask_ferdig(id) values (:id)""",
                    mapOf("id" to eventPerLinjeLagret.id)
                ).asUpdate
            )
        }
    }
}
