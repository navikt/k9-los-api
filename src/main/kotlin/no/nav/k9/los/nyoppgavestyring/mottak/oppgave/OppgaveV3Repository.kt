package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveV3Repository(
    private val dataSource: DataSource
) {

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

    //TODO: status enum

    fun lagre(oppgave: OppgaveV3, tx: TransactionalSession) {
        val (eksisterendeId, eksisterendeVersjon) = hentVersjon(tx, oppgave)

        eksisterendeId?.let { deaktiverVersjon(eksisterendeId, oppgave.endretTidspunkt, tx) }

        val nyVersjon = eksisterendeVersjon?.plus(1) ?: 0

        val oppgaveId = lagre(oppgave, nyVersjon, tx)
        lagreFeltverdier(oppgaveId, oppgave.felter, tx)
    }

    fun hentAktivOppgave(eksternId: String, oppgavetype: Oppgavetype, tx: TransactionalSession): OppgaveV3? {
        return tx.run(
            queryOf(
                """
                    select * from oppgave_v3 where ekstern_id = :eksternId and aktiv = true
                """.trimIndent(), mapOf("eksternId" to eksternId)
            ).map { row ->
                OppgaveV3(
                    id = row.long("id"),
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetype,
                    status = Oppgavestatus.valueOf(row.string("status")),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentFeltverdier(row.long("id"), oppgavetype, tx)
                )
            }.asSingle
        )
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
                    "status" to oppgave.status.toString(),
                    "endretTidspunkt" to oppgave.endretTidspunkt,
                    "versjon" to nyVersjon,
                    "aktiv" to true,
                    "kildeomrade" to oppgave.kildeområde
                )
            )
        )!!
    }

    private fun hentFeltverdier(
        oppgaveId: Long,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi where oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId)
            ).map { row ->
                OppgaveFeltverdi(
                    id = row.long("id"),
                    oppgavefelt = oppgavetype.oppgavefelter.first { oppgavefelt ->
                        oppgavefelt.id == row.long("oppgavefelt_id")
                    },
                    verdi = row.string("verdi")
                )
            }.asList
        )
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

    fun slettOppgaverOgFelter() {
        using(sessionOf(dataSource)) { session ->
            log.info("trunkerer oppgavetabeller")
            session.transaction { tx ->
                tx.run(
                    queryOf("""truncate table oppgave_v3_sendt_dvh, oppgavefelt_verdi, oppgave_v3, oppgavefelt, oppgavetype, feltdefinisjon""").asUpdate
                )
            }
            log.info("resetter dirtyflagg på k9sak-eventer")
            session.transaction { tx ->
                tx.run(
                    queryOf("""update behandling_prosess_events_k9 set dirty = true""").asUpdate
                )
            }
            log.info("resetter dirtyflagg på k9klage-eventer")
            session.transaction { tx ->
                tx.run(
                    queryOf("""update behandling_prosess_events_klage set dirty = true""").asUpdate
                )
            }
        }
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