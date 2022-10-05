package no.nav.k9.nyoppgavestyring.mottak.oppgave

import io.ktor.http.*
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveV3Repository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

    //TODO: status enum

    fun lagre(oppgave: OppgaveV3, tx: TransactionalSession) {
        // hente ut nyeste versjon(ekstern_id, område) i basen, sette aktuell versjon til inaktiv
        val (eksisterendeId, eksisterendeVersjon) = hentVersjon(tx, oppgave)

        eksisterendeId?.let { deaktiverVersjon(eksisterendeId, oppgave.endretTidspunkt, tx) }

        val nyVersjon = eksisterendeVersjon?.plus(1) ?: 0

        val startLagreOppgave = System.currentTimeMillis()
        log.info("Lagret oppgaveV3, tidsbruk: ${System.currentTimeMillis() - startLagreOppgave}")
        val oppgaveId = lagre(oppgave, nyVersjon, tx)
        val startLagreFeltverdier = System.currentTimeMillis()
        lagreFeltverdier(oppgaveId, oppgave.felter, tx)
        log.info("Lagret feltverdier, tidsbruk: ${System.currentTimeMillis() - startLagreFeltverdier}")
    }

    private fun lagre(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession): Long {
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    insert into oppgave_v3(ekstern_id, ekstern_versjon, oppgavetype_id, status, versjon, aktiv, kildeomrade, endret_tidspunkt)
                    values(:eksternId, :eksternVersjon, :oppgavetypeId, :status, :versjon, :aktiv, :kildeomrade, :endretTidspunkt)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "eksternVersjon" to oppgave.eksternVersjon,
                    "oppgavetypeId" to oppgave.oppgavetype.id,
                    "status" to oppgave.status,
                    "endretTidspunkt" to oppgave.endretTidspunkt,
                    "versjon" to nyVersjon,
                    "aktiv" to true,
                    "kildeomrade" to oppgave.kildeområde
                )
            )
        )!!
    }

    private fun lagreFeltverdier(
        oppgaveId: Long,
        oppgaveFeltverdier: List<OppgaveFeltverdi>,
        tx: TransactionalSession
    ) {
        tx.batchPreparedNamedStatement("""
            insert into oppgavefelt_verdi(oppgave_id, oppgavefelt_id, verdi)
                    VALUES (:oppgaveId, :oppgavefeltId, :verdi)
        """.trimIndent(),
            oppgaveFeltverdier.map { feltverdi ->
                mapOf(
                    "oppgaveId" to oppgaveId,
                    "oppgavefeltId" to feltverdi.oppgavefelt.id,
                    "verdi" to feltverdi.verdi
                )
            }
        )
    }

    private fun hentVersjon(tx: TransactionalSession, oppgave: OppgaveV3): Pair<Long?, Long?> {
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
        ) ?: Pair(null, null)
    }

    private fun deaktiverVersjon(eksisterendeId: Long, deaktivertTidspunkt: LocalDateTime, tx: TransactionalSession) {
        val deaktiverVersjon = System.currentTimeMillis()
        tx.run(
            queryOf(
                """
                update oppgave_v3 set aktiv = false, deaktivert_tidspunkt = :deaktivertTidspunkt where id = :id
            """.trimIndent(),
                mapOf(
                    "id" to eksisterendeId,
                    "deaktivertTidspunkt" to deaktivertTidspunkt
                )
            ).asUpdate
        )
        log.info("Deaktiverte forrige versjon, tidsbruk: ${System.currentTimeMillis() - deaktiverVersjon}")
    }

    fun finnesFraFør(tx: TransactionalSession, eksternId: String, eksternVersjon: String): Boolean {
        return tx.run(
            queryOf(
                """
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

    fun tellAntall(): Pair<Long, Long> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    with antallAlle as (
                        select count(*) as antallAlle
                        from oppgave_v3
                    ), antallAktive as (
                        select count(*) as antallAktive
                        from oppgave_v3
                        where aktiv = true
                    )
                    select antallAlle, antallAktive
                    from antallAlle, antallAktive
                """.trimIndent()
                ).map { row ->
                    Pair(
                        first = row.long("antallAlle"),
                        second = row.long("antallAktive")
                    )
                }.asSingle
            )!!
        }
    }

}