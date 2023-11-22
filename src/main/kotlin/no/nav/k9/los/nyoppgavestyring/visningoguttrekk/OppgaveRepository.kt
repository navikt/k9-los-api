package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository

class OppgaveRepository(
    private val oppgavetypeRepository: OppgavetypeRepository
) {

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
                val kildeområde = row.string("kildeomrade")
                val oppgaveTypeId = row.long("oppgavetype_id")
                Oppgave(
                    eksternId = eksternId,
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx),
                    status = row.string("status"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentOppgavefelter(tx, row.long("id")),
                    versjon = row.int("versjon")
                )
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med eksternId $eksternId")

        return oppgave.fyllDefaultverdier()
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
                val kildeområde = row.string("kildeomrade")
                val oppgaveTypeId = row.long("oppgavetype_id")
                Oppgave(
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx),
                    status = row.string("status"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentOppgavefelter(tx, row.long("id")),
                    versjon = row.int("versjon")
                )
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

        return oppgave.fyllDefaultverdier()
    }

    private fun Oppgave.fyllDefaultverdier(): Oppgave {
        val defaultverdier = oppgavetype.oppgavefelter
            .filter { oppgavefelt -> oppgavefelt.påkrevd }
            .mapNotNull { påkrevdFelt ->
                if (felter.find { it.eksternId == påkrevdFelt.feltDefinisjon.eksternId && !påkrevdFelt.feltDefinisjon.listetype } == null) {
                    Oppgavefelt(
                        eksternId = påkrevdFelt.feltDefinisjon.eksternId,
                        område = kildeområde,
                        listetype = false, //listetyper er aldri påkrevd
                        påkrevd = true,
                        verdi = påkrevdFelt.defaultverdi.toString()
                    )
                } else null
            }

        return copy(felter = felter.plus(defaultverdier))
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