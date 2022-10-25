package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

class StatistikkRepository(private val dataSource: DataSource) {

    fun hentOppgaverSomIkkeErSendt(): List<Long> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select id
                        from oppgave_v3 ov
                            	inner join oppgavetype o on ov.oppgavetype_id = o.id 
                        where o.ekstern_id = 'k9-sak'  
                        and not exists (select * from OPPGAVE_V3_SENDT_DVH os where os.id = ov.id)
                    """.trimIndent()
                )
                    .map { row ->
                        row.long("id")
                    }.asList
            )
        }
    }

    fun kvitterSending(id: Long) {
        using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        insert into OPPGAVE_V3_SENDT_DVH(id) values (:id)
                    """.trimIndent(),
                    mapOf("id" to id)
                ).asUpdate
            )
        }
    }

    fun fjernSendtMarkering() {
        using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """delete from oppgave_v3_sendt_dvh"""
                ).asUpdate
            )
        }
    }
}