package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.aksjonspunktbehandling.objectMapper
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import java.util.*
import javax.sql.DataSource


class EventRepository(
    private val dataSource: DataSource,
) {

    fun lagre(event: PunsjEventV3Dto): Long? {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val json = objectMapper().writeValueAsString(event)
                tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                            insert into eventlager_punsj(ekstern_id, eventnr_for_oppgave, "data", dirty) 
                            values (:ekstern_id,
                            (select coalesce(max(eventnr_for_oppgave)+1, 0) from behandling_prosess_events_punsj where ekstern_id = :ekstern_id),
                            :data :: jsonb,
                            true)
                         """,
                        mapOf(
                            "ekstern_id" to event.eksternId,
                            "data" to json
                        )
                    )
                )
            }
        }
    }

    fun hent(eksternId: String, eventNr: Long): PunsjEventV3Dto {
        val json = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select data
                        from eventlager_punsj  
                        where ekstern_id = :ekstern_id
                        and eventnr_for_oppgave = :eventnr
                    """.trimIndent(),
                    mapOf(
                        "ekstern_id" to eksternId,
                        "eventNr" to eventNr
                    )
                ).map { row ->
                    row.string("data")
                }.asSingle
            )
        }
        json?.let {
            return objectMapper().readValue(json, PunsjEventV3Dto::class.java)
        } ?: throw FinnerIkkeDataException("Fant ingen Punsj-event med eksternId: $eksternId og eventNr: $eventNr")
    }

    fun hentAlleEventer(eksternId: String): List<PunsjEventV3Dto> {
        val eventerJson = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select data
                    from eventlager_punsj bpep 
                    where ekstern_id = :ekstern_id
                    order by eventnr_for_oppgave ASC
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    row.string("data")
                }.asList
            )
        }
        return eventerJson
            .map { event -> objectMapper().readValue(event, PunsjEventV3Dto::class.java) }
            .toList()
    }

    fun hentAlleDirtyEventer(eksternId: String): List<PunsjEventV3Dto> {
        val eventerJson = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select data
                    from eventlager_punsj 
                    where ekstern_id = :ekstern_id
                    and dirty = true
                    order by eventnr_for_oppgave ASC
                """.trimIndent(),
                    mapOf("ekstern_id" to eksternId)
                ).map { row ->
                    row.string("data")
                }.asList
            )
        }
        return eventerJson
            .map { event -> objectMapper().readValue(event, PunsjEventV3Dto::class.java) }
            .toList()
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

    fun hentAlleEventIderUtenVasketHistorikk(): List<UUID> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                            select * 
                            from eventlager_punsj e
                            where not exists (select * from eventlager_punsj_historikkvask_ferdig hv where hv.id = e.id)
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
                """insert into eventlager_punsj_historikkvask_ferdig(id) values (:uuid)""",
                mapOf("uuid" to uuid.toString())
            ).asUpdate
        )
    }
}
