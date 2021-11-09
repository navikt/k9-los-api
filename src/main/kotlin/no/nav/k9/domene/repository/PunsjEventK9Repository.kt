package no.nav.k9.domene.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.domene.modell.K9PunsjModell
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.tjenester.innsikt.Databasekall
import no.nav.k9.tjenester.innsikt.Mapping
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
            log.info("TEST213")
            return K9PunsjModell(emptyList())
        }
        return try {
            log.info("TEST321")
            val modell = objectMapper().readValue(json, K9PunsjModell::class.java)
            K9PunsjModell(modell.eventer.sortedBy { it.eventTid })
        } catch (e: Exception) {
            log.error("", e)
            log.info("TEST123")
            K9PunsjModell(emptyList())
        }
    }

    fun lagre(
        event: PunsjEventDto
    ): K9PunsjModell {
        val json = objectMapper().writeValueAsString(event)

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
        val modell = objectMapper().readValue(out!!, K9PunsjModell::class.java)
        return modell.copy(eventer = modell.eventer.sortedBy { it.eventTid })
    }

    fun hentAlleEventerIder(
    ): List<String> {

        val ider = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_k9",
                        mapOf()
                    )
                        .map { row ->
                            row.string("id")
                        }.asList
                )
            }

        }
        return ider

    }

    fun eldsteEventTid(): String {
        val json: String? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """select sort_array(data->'eventer', 'eventTid') -> 0 ->'eventTid' as data from behandling_prosess_events_k9 order by (sort_array(data->'eventer', 'eventTid') -> 0 ->'eventTid') limit 1;""",
                    mapOf()
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        return json!!
    }

    fun mapMellomeksternIdOgBehandlingsid(): List<Mapping> {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        return using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """select id, (data-> 'eventer' -> -1 ->'behandlingId' ) as behandlingid from behandling_prosess_events_k9""",
                    mapOf()
                )
                    .map { row ->
                        Mapping(id = row.string("behandlingid"), uuid = row.string("id"))
                    }.asList
            )
        }
    }
}
