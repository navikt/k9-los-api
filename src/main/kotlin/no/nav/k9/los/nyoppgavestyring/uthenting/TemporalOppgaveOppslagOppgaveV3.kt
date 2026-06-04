package no.nav.k9.los.nyoppgavestyring.uthenting

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import java.time.LocalDateTime

class TemporalOppgaveOppslagOppgaveV3(
    val oppgavetypeRepository: OppgavetypeRepository,
    val transactionalManager: TransactionalManager
) : TemporalOppgaveOppslag {

    private fun mapOppgave(
        row: Row, now: LocalDateTime, tx: TransactionalSession
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
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
        ).fyllDefaultverdier().utledTransienteFelter(now)
    }


    private fun hentOppgavefelter(tx: TransactionalSession, oppgaveId: Long): List<Oppgavefelt> {
        return tx.run(
            queryOf(
                """
                select fd.ekstern_id as ekstern_id, o.ekstern_id as omrade, fd.liste_type, f.pakrevd, ov.verdi, ov.verdi_bigint
                from oppgavefelt_verdi ov 
                inner join oppgavefelt f on ov.oppgavefelt_id = f.id 
                inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id 
                inner join omrade o on fd.omrade_id = o.id 
                where ov.oppgave_id = :oppgaveId
                order by fd.ekstern_id
                """.trimIndent(), mapOf("oppgaveId" to oppgaveId)
            ).map { row ->
                Oppgavefelt(
                    eksternId = row.string("ekstern_id"),
                    område = row.string("omrade"),
                    listetype = row.boolean("liste_type"),
                    påkrevd = row.boolean("pakrevd"),
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }

    override fun hentTidsserie(
        oppgavetypeEksternId: String,
        oppgaveEksternId: String,
    ): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            hentTidsserie(oppgavetypeEksternId, oppgaveEksternId, tx)
        }
    }

    override fun hentTidsserie(
        oppgavetypeEksternId: String,
        eksternId: String,
        tx: TransactionalSession
    ): List<Oppgave> {
        val now = LocalDateTime.now()
        return tx.run(
            queryOf(
                """
                    select o.*
                    from oppgave_v3 o
                        inner join oppgavetype ot on o.oppgavetype_id = ot.id
                        inner join omrade omr on ot.omrade_id = omr.id
                    where omr.ekstern_id = :omrade
                      and ot.ekstern_id = :oppgavetype
                      and o.ekstern_id = :oppgaveEksternId
                    order by o.versjon asc
                """.trimIndent(),
                mapOf(
                    "omrade" to "K9",
                    "oppgavetype" to oppgavetypeEksternId,
                    "oppgaveEksternId" to eksternId,
                )
            ).map { row -> mapOppgave(row, now, tx) }.asList
        )
    }

    override fun hentOppgaveForTidspunkt(
        oppgavetypeEksternId: String, eksternId: String, tidspunkt: LocalDateTime
    ): Oppgave? {
        TODO("Not yet implemented")
    }

    override fun hentOppgaveForTidspunkt(
        oppgavetypeEksternId: String,
        eksternId: String,
        tidspunkt: LocalDateTime,
        tx: TransactionalSession
    ): Oppgave? {
        TODO("Not yet implemented")
    }
}