package no.nav.k9.nyoppgavestyring.oppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.slf4j.LoggerFactory

class OppgaveV3Repository {

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

    //TODO: status enum

    fun lagre(oppgave: OppgaveV3, tx: TransactionalSession) {
        // hente ut nyeste versjon(ekstern_id, område) i basen, sette aktuell versjon til inaktiv
        val (eksisterendeId, eksisterendeVersjon) = hentVersjon(tx, oppgave) ?: Pair(null, null) //TODO: Herregud så stygt!

        eksisterendeId?.let { deaktiverVersjon(eksisterendeId, tx) }

        val nyVersjon = eksisterendeVersjon?.plus(1) ?: 0

        val oppgaveId = lagre(oppgave, nyVersjon, tx)
        lagreFeltverdier(oppgaveId, oppgave.felter, tx)
    }

    private fun lagre(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession): Long  {
        return tx.updateAndReturnGeneratedKey(
            queryOf("""
                    insert into oppgave_v3(ekstern_id, ekstern_versjon, oppgavetype_id, status, versjon, aktiv, kildeomrade, endret_tidspunkt)
                    values(:eksternId, :eksternVersjon, :oppgavetypeId, :status, :versjon, :aktiv, :kildeomrade, CURRENT_TIMESTAMP)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "eksternVersjon" to oppgave.eksternVersjon,
                    "oppgavetypeId" to oppgave.oppgavetype.id,
                    "status" to oppgave.status,
                    "versjon" to nyVersjon,
                    "aktiv" to true,
                    "kildeomrade" to oppgave.kildeområde
                )
            )
        )!!
    }

    private fun lagreFeltverdier(oppgaveId: Long, oppgaveFeltverdier: Set<OppgaveFeltverdi>, tx: TransactionalSession) {
        oppgaveFeltverdier.forEach { feltverdi ->
            tx.run(
                queryOf(
                    """
                    insert into oppgavefelt_verdi(oppgave_id, oppgavefelt_id, verdi)
                    VALUES(:oppgaveId, :oppgavefeltId, :verdi)""".trimIndent(),
                    mapOf(
                        "oppgaveId" to oppgaveId,
                        "oppgavefeltId" to feltverdi.oppgavefelt.id,
                        "verdi" to  feltverdi.verdi
                    )
                ).asUpdate
            )
        }
    }

    private fun hentVersjon(tx: TransactionalSession, oppgave: OppgaveV3): Pair<Long, Long>? {
        //TODO: konsistenssjekk - skrive om til å hente alle oppgavene for gitt eksternId og sjekke at en og bare en versjon er aktiv = true
        return tx.run(
            queryOf(
                """
                select versjon, id
                from oppgave_v3 o
                where o.ekstern_id = :eksternId
                and o.kildeomrade = :omrade
                and versjon = 
                    (select max(versjon)
                     from oppgave_v3 oi
                     where oi.ekstern_id = o.ekstern_id
                     and oi.kildeomrade = o.kildeomrade)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "omrade" to oppgave.kildeområde
                )
            ).map { row -> Pair(row.long("id"), row.long("versjon")) }.asSingle
        )
    }

    private fun hentOppgavefelter(tx: TransactionalSession, oppgave: OppgaveV3): Set<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                select *
                from oppgavefelt_verdi ov 
                where ov.oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgave.id)
            ).map { row ->
                OppgaveFeltverdi(
                    id = row.long("id"),
                    oppgavefelt = oppgave.oppgavetype.oppgavefelter.find { oppgavefelt ->
                        oppgavefelt.id == row.long("oppgavefelt_id")
                    } ?: throw IllegalStateException("Oppgavetype mangler oppgavens angitte oppgavefelt"),
                    verdi = row.string("verdi")
                )
            }.asList
        ).toSet()
    }

    private fun deaktiverVersjon(eksisterendeId: Long, tx: TransactionalSession) {
        tx.run(
            queryOf("""
                update oppgave_v3 set aktiv = false where id = :id
            """.trimIndent(),
                mapOf("id" to eksisterendeId)
            ).asUpdate
        )
    }

    fun idempotensMatch(tx: TransactionalSession, oppgaveDto: OppgaveDto): Boolean {
        return finnesEksternIdOgEksternVersjon(tx, oppgaveDto.id, oppgaveDto.versjon) ||
                finnesSammeOppgaveMedIdentiskStatus(tx, oppgaveDto.id, oppgaveDto.status)
    }
    private fun finnesEksternIdOgEksternVersjon(tx: TransactionalSession, eksternId: String, eksternVersjon: String): Boolean {
        return tx.run(
            queryOf("""
                    select exists(
                        select *
                        from oppgave_v3 ov 
                        where ekstern_id = :eksternId
                        and ekstern_versjon = :eksternVersjon
                    )
                """.trimIndent(),
                mapOf(
                    "eksternId" to eksternId,
                    "eksternVersjon" to eksternVersjon
                )
            ).map { row -> row.boolean(1) }.asSingle
        )!!
    }

    private fun finnesSammeOppgaveMedIdentiskStatus(tx: TransactionalSession, eksternId: String, status: String): Boolean {
        return tx.run(
            queryOf("""
                select exists(
                    select * 
                    from oppgave_v3 
                    where ekstern_id = :eksternId 
                    and status = :status
                )
            """.trimIndent(),
            mapOf("eksternId" to eksternId, "status" to status)
            ).map { row -> row.boolean(1) }.asSingle
        )!!
    }
}