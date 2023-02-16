package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository

class OppgaveRepository(private val oppgavetypeRepository: OppgavetypeRepository) {

    fun hentNyesteOppgaveForEksternId(tx: TransactionalSession, eksternId: String): Oppgave {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.ekstern_id = :eksternId
                and ov.versjon = (select max(versjon) from oppgave_v3 ov2 where ov2.ekstern_id = :eksternId)
            """.trimIndent(),
                mapOf("eksternId" to eksternId)
            ).map { row ->
                Oppgave(
                    eksternId = eksternId,
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetypeId = row.long("oppgavetype_id"),
                    status = row.string("status"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentOppgavefelter(tx, row.long("id"))
                )
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med eksternId $eksternId")

        return fyllDefaultverdier(oppgave, tx)
    }

    fun hentOppgaveForId(tx: TransactionalSession, id: Long): Oppgave {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.id = :id
            """.trimIndent(),
                mapOf("id" to id)
            ).map { row ->
                Oppgave(
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetypeId = row.long("oppgavetype_id"),
                    status = row.string("status"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentOppgavefelter(tx, row.long("id"))
                )
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

        return fyllDefaultverdier(oppgave, tx)
    }

    private fun fyllDefaultverdier(
        oppgave: Oppgave,
        tx: TransactionalSession
    ): Oppgave {
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(oppgave.kildeområde, oppgave.oppgavetypeId, tx)
        val defaultverdier = mutableListOf<Oppgavefelt>()

        oppgavetype.oppgavefelter
            .filter { oppgavefelt -> oppgavefelt.påkrevd }
            .forEach { påkrevdFelt ->
                if (oppgave.felter.find { it.eksternId.equals(påkrevdFelt.feltDefinisjon.eksternId) } == null) {
                    defaultverdier.add(
                        Oppgavefelt(
                            eksternId = påkrevdFelt.feltDefinisjon.eksternId,
                            område = oppgave.kildeområde,
                            listetype = false, //listetyper er aldri påkrevd
                            påkrevd = true,
                            verdi = påkrevdFelt.defaultverdi.toString()
                        )
                    )
                }
            }

        return oppgave.copy(felter = oppgave.felter.plus(defaultverdier))
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