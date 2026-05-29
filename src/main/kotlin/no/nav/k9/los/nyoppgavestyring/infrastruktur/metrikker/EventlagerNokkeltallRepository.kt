package no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

data class NokkeltallPerFagsystem(val fagsystem: String, val antall: Long)
data class UsendtStatistikkPerOppgavetype(val oppgavetypeEksternId: String, val antall: Long)

class EventlagerNokkeltallRepository(private val dataSource: DataSource) {

    fun hentAntallDirtyEventerPerFagsystem(): List<NokkeltallPerFagsystem> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select en.fagsystem, count(*) as antall
                    from event e
                        join event_nokkel en on e.event_nokkel_id = en.id
                    where e.dirty = true
                    group by en.fagsystem
                    order by en.fagsystem
                    """.trimIndent()
                ).map { row ->
                    NokkeltallPerFagsystem(
                        fagsystem = row.string("fagsystem"),
                        antall = row.long("antall")
                    )
                }.asList
            )
        }
    }

    fun hentAntallDirtyEventnoklerPerFagsystem(): List<NokkeltallPerFagsystem> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select en.fagsystem, count(*) as antall
                    from event_nokkel en
                    where exists (
                        select 1
                        from event e
                        where e.event_nokkel_id = en.id
                        and e.dirty = true
                    )
                    group by en.fagsystem
                    order by en.fagsystem
                    """.trimIndent()
                ).map { row ->
                    NokkeltallPerFagsystem(
                        fagsystem = row.string("fagsystem"),
                        antall = row.long("antall")
                    )
                }.asList
            )
        }
    }

    fun hentAntallHistorikkvaskbestillingerPerFagsystem(): List<NokkeltallPerFagsystem> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select en.fagsystem, count(*) as antall
                    from event_historikkvask_bestilt hb
                        join event_nokkel en on hb.event_nokkel_id = en.id
                    group by en.fagsystem
                    order by en.fagsystem
                    """.trimIndent()
                ).map { row ->
                    NokkeltallPerFagsystem(
                        fagsystem = row.string("fagsystem"),
                        antall = row.long("antall")
                    )
                }.asList
            )
        }
    }

    fun hentUsendtOppgavestatistikkPerOppgavetype(): List<UsendtStatistikkPerOppgavetype> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select o.ekstern_id as oppgavetype_ekstern_id, count(*) as antall
                    from oppgave_v3 ov
                    join oppgavetype o
                        on ov.oppgavetype_id = o.id
                    left join oppgave_v3_sendt_dvh_ekstern os
                        on os.ekstern_id = ov.ekstern_id
                        and os.ekstern_versjon = ov.ekstern_versjon
                    where o.ekstern_id in ('k9sak', 'k9klage')
                        and os.ekstern_id is null
                    group by o.ekstern_id
                    """.trimIndent()
                ).map { row ->
                    UsendtStatistikkPerOppgavetype(
                        oppgavetypeEksternId = row.string("oppgavetype_ekstern_id"),
                        antall = row.long("antall")
                    )
                }.asList
            )
        }
    }
}

