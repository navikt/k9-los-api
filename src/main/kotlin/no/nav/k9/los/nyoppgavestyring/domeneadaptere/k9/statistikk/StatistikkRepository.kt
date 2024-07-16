package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import java.time.LocalDateTime
import javax.sql.DataSource

class StatistikkRepository(
    private val dataSource: DataSource,
    private val oppgavetypeRepository: OppgavetypeRepository
) {

    fun hentOppgaverSomIkkeErSendt(): List<Long> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select ov.id
                        from oppgave_v3 ov
                            	join oppgavetype o ON ov.oppgavetype_id = o.id 
                        where o.ekstern_id in ('k9sak', 'k9klage')
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

    fun hentOppgaveForId(tx: TransactionalSession, id: Long, now: LocalDateTime = LocalDateTime.now()): Oppgave {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.id = :id
            """.trimIndent(),
                mapOf("id" to id)
            ).map { row ->
                mapOppgave(row, now, tx)
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

        return oppgave
    }

    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Oppgave {
        val kildeområde = row.string("kildeomrade")
        val oppgaveTypeId = row.long("oppgavetype_id")
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx)
        val oppgavefelter = hentOppgavefelter(tx, row.long("id"))
        return Oppgave(
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            oppgavetype = oppgavetype,
            status = row.string("status"),
            endretTidspunkt = row.localDateTime("endret_tidspunkt"),
            kildeområde = row.string("kildeomrade"),
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
            versjon = row.int("versjon")
        ).fyllDefaultverdier().utledTransienteFelter(now)
    }

    private fun hentOppgavefelter(tx: TransactionalSession, oppgaveId: Long): List<Oppgavefelt> {
        return tx.run(
            queryOf(
                """
                select fd.ekstern_id as ekstern_id, o.ekstern_id as omrade, fd.liste_type, f.pakrevd, ov.verdi
                from oppgavefelt_verdi ov 
                inner join oppgavefelt f on ov.oppgavefelt_id = f.id 
                inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id 
                inner join omrade o on fd.omrade_id = o.id 
                where ov.oppgave_id = :oppgaveId
                order by fd.ekstern_id
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId)
            ).map { row ->
                Oppgavefelt(
                    eksternId = row.string("ekstern_id"),
                    område = row.string("omrade"),
                    listetype = row.boolean("liste_type"),
                    påkrevd = row.boolean("pakrevd"),
                    verdi = row.string("verdi")
                )
            }.asList
        )
    }
}