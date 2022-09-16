package no.nav.k9.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession
import kotliquery.queryOf

class OppgaveRepository {

    fun hentOppgaveMedHistoriskeVersjoner(tx: TransactionalSession, eksternId: String): Set<Oppgave> {
        return tx.run(
            queryOf(
                """
                    select * 
                    from oppgave_v3 ov
                    where ov.ekstern_id = :eksternId
                    order by versjon asc
                """.trimIndent(),
                mapOf("eksternId" to eksternId)
            ).map { row ->
                Oppgave(
                    eksternId = eksternId,
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = row.string("oppgavetype"),
                    status = row.string("status"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentOppgavefelter(tx, row.long("id"))
                )
            }.asList
        ).toSet()
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
                    listetype = row.boolean("listetype"),
                    påkrevd = row.boolean("pakrevd"),
                    verdi = row.string("verdi"),
                )
            }.asList
        )
    }

}