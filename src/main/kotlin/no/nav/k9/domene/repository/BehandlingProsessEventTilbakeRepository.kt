package no.nav.k9.domene.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.domene.modell.K9TilbakeModell
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.tjenester.innsikt.Databasekall
import no.nav.k9.tjenester.innsikt.Mapping
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource


class BehandlingProsessEventTilbakeRepository(private val dataSource: DataSource) {
    fun hent(uuid: UUID): K9TilbakeModell {
        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from behandling_prosess_events_tilbake where id = :id",
                    mapOf("id" to uuid.toString())
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){ LongAdder() }.increment()
        if (json.isNullOrEmpty()) {
            return K9TilbakeModell(mutableListOf())
        }
        val modell = objectMapper().readValue(json, K9TilbakeModell::class.java)
     
        return K9TilbakeModell(  modell.eventer.sortedBy { it.eventTid }.toMutableList())
    }

    fun lagre(
        event: BehandlingProsessEventTilbakeDto
    ): K9TilbakeModell {
        val json = objectMapper().writeValueAsString(event)

        val id = event.eksternId.toString()
        val out = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    insert into behandling_prosess_events_tilbake as k (id, data)
                    values (:id, :dataInitial :: jsonb)
                    on conflict (id) do update
                    set data = jsonb_set(k.data, '{eventer,999999}', :data :: jsonb, true)
                 """, mapOf("id" to id, "dataInitial" to "{\"eventer\": [$json]}", "data" to json)
                    ).asUpdate
                )
                tx.run(
                    queryOf(
                        "select data from behandling_prosess_events_tilbake where id = :id",
                        mapOf("id" to id)
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )
            }

        }
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        val modell = objectMapper().readValue(out!!, K9TilbakeModell::class.java)
        return modell.copy(eventer = modell.eventer.sortedBy { it.eventTid })
    }

    fun hentAlleEventerIder(
    ): List<String> {

        val ider = using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select id from behandling_prosess_events_tilbake",
                        mapOf()
                    )
                        .map { row ->
                            row.string("id")
                   }.asList
                )
            }

        }
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        return ider

    }

    fun eldsteEventTid(): String {
        val json: String? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """select sort_array(data->'eventer', 'eventTid') -> 0 ->'eventTid' as data from behandling_prosess_events_tilbake order by (sort_array(data->'eventer', 'eventTid') -> 0 ->'eventTid') limit 1;""",
                    mapOf()
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        }
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        return  json!!
    }
    
    fun mapMellomeksternIdOgBehandlingsid(): List<Mapping> {
        Databasekall.map.computeIfAbsent(object{}.javaClass.name + object{}.javaClass.enclosingMethod.name){LongAdder()}.increment()
        return using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """select id, (data-> 'eventer' -> -1 ->'behandlingId' ) as behandlingid from behandling_prosess_events_tilbake""",
                    mapOf()
                )
                    .map { row ->
                        Mapping(id = row.string("behandlingid"), uuid = row.string("id"))                        
                    }.asList
            )
        }
    }
}
