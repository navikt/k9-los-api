package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
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
                        join oppgavetype o
                            on ov.oppgavetype_id = o.id
                        left join (
                            select ofv.oppgave_id, max(ofv.verdi) as saksnummer
                            from oppgavefelt_verdi ofv
                            join oppgavefelt f
                                on ofv.oppgavefelt_id = f.id
                            join feltdefinisjon fd
                                on f.feltdefinisjon_id = fd.id
                            join omrade om
                                on fd.omrade_id = om.id
                            where fd.ekstern_id = 'saksnummer'
                                and om.ekstern_id = 'K9'
                            group by ofv.oppgave_id
                        ) saksnummer
                            on saksnummer.oppgave_id = ov.id
                        left join oppgave_v3_sendt_dvh_ekstern os
                            on os.ekstern_id = ov.ekstern_id
                            and os.ekstern_versjon = ov.ekstern_versjon
                        where o.ekstern_id in ('k9sak', 'k9klage')
                            and os.ekstern_id is null
                        order by saksnummer.saksnummer nulls last, ov.ekstern_id, ov.ekstern_versjon
                    """.trimIndent()
                ).map { row -> row.long("id") }.asList
            )
        }
    }

    fun kvitterSending(id: Long) {
        using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        insert into OPPGAVE_V3_SENDT_DVH_EKSTERN(ekstern_id, ekstern_versjon)
                        select ov.ekstern_id, ov.ekstern_versjon from oppgave_v3 ov where ov.id = :id
                        on conflict (ekstern_id, ekstern_versjon) do nothing
                    """.trimIndent(),
                    mapOf("id" to id)
                ).asUpdate
            )
        }
    }

    fun kvitterSending(tx: TransactionalSession, id: Long) {
        tx.run(
            queryOf(
                """
                    insert into OPPGAVE_V3_SENDT_DVH_EKSTERN(ekstern_id, ekstern_versjon)
                    select ov.ekstern_id, ov.ekstern_versjon from oppgave_v3 ov where ov.id = :id
                    on conflict (ekstern_id, ekstern_versjon) do nothing
                """.trimIndent(),
                mapOf("id" to id)
            ).asUpdate
        )
    }


    fun fjernSendtMarkering(oppgave: OppgaveNøkkelDto, tx: TransactionalSession) {
        tx.run(
            queryOf("""
                delete from OPPGAVE_V3_SENDT_DVH_EKSTERN
                where ekstern_id = :id
            """.trimIndent()
                , mapOf("id" to oppgave.oppgaveEksternId)
            ).asUpdate
        )
    }

    fun fjernSendtMarkering(oppgavetype: String? = null) {
        using(sessionOf(dataSource)) {
            if (oppgavetype != null) {
                it.run(
                    queryOf(
                        """
                        delete from OPPGAVE_V3_SENDT_DVH_EKSTERN e
                        where e.ekstern_id IN (
                            SELECT ov3.ekstern_id FROM oppgave_v3 ov3
                            join oppgavetype ot ON ov3.oppgavetype_id = ot.id
                            WHERE ot.ekstern_id = :oppgavetype
                        )
                        """.trimIndent()
                    , mapOf("oppgavetype" to oppgavetype)
                    ).asUpdate
                )
            } else {
                it.run(
                    queryOf(
                        """delete from OPPGAVE_V3_SENDT_DVH_EKSTERN"""
                    ).asUpdate
                )
            }
        }
    }

    fun hentOppgaveForId(tx: TransactionalSession, id: Long, now: LocalDateTime = LocalDateTime.now()): Pair<Oppgave, Int> {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.id = :id
            """.trimIndent(),
                mapOf("id" to id)
            ).map { row ->
                mapOppgave(row, now, tx, hentOppgavefelter(tx, row.long("id")))
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

        return oppgave
    }

    fun hentOppgaverForIder(tx: TransactionalSession, ider: List<Long>, now: LocalDateTime = LocalDateTime.now()): List<Pair<Long, Pair<Oppgave, Int>>> {
        if (ider.isEmpty()) return emptyList()

        val params = ider.mapIndexed { index, id -> "id${index + 1}" to id }.toMap()
        val placeholders = ider.indices.joinToString(",") { ":id${it + 1}" }

        // Batch-hent alle oppgavefelter for alle oppgave-IDer i én spørring
        val alleOppgavefelter = hentOppgavefelterBatch(tx, ider)

        return tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.id IN ($placeholders)
            """.trimIndent(),
                params
            ).map { row ->
                val oppgaveId = row.long("id")
                val felter = alleOppgavefelter[oppgaveId] ?: emptyList()
                oppgaveId to mapOppgave(row, now, tx, felter)
            }.asList
        )
    }

    fun kvitterSendingBatch(tx: TransactionalSession, ider: List<Long>) {
        if (ider.isEmpty()) return

        val params = ider.mapIndexed { index, id -> "id${index + 1}" to id }.toMap()
        val placeholders = ider.indices.joinToString(",") { ":id${it + 1}" }

        tx.run(
            queryOf(
                """
                    insert into OPPGAVE_V3_SENDT_DVH_EKSTERN(ekstern_id, ekstern_versjon)
                    select ov.ekstern_id, ov.ekstern_versjon from oppgave_v3 ov where ov.id IN ($placeholders)
                    on conflict (ekstern_id, ekstern_versjon) do nothing
                """.trimIndent(),
                params
            ).asUpdate
        )
    }

    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession,
        oppgavefelter: List<Oppgavefelt>,
    ): Pair<Oppgave, Int> {
        val kildeområde = row.string("kildeomrade")
        val oppgaveTypeId = row.long("oppgavetype_id")
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx)
        return Oppgave(
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            oppgavetype = oppgavetype,
            status = row.string("status"),
            endretTidspunkt = row.localDateTime("endret_tidspunkt"),
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
        ).fyllDefaultverdier().utledTransienteFelter(now) to row.int("versjon")
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
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId)
            ).map { row ->
                mapOppgavefelt(row)
            }.asList
        )
    }

    private fun hentOppgavefelterBatch(tx: TransactionalSession, oppgaveIder: List<Long>): Map<Long, List<Oppgavefelt>> {
        if (oppgaveIder.isEmpty()) return emptyMap()

        val params = oppgaveIder.mapIndexed { index, id -> "id${index + 1}" to id }.toMap()
        val placeholders = oppgaveIder.indices.joinToString(",") { ":id${it + 1}" }

        return tx.run(
            queryOf(
                """
                select ov.oppgave_id, fd.ekstern_id as ekstern_id, o.ekstern_id as omrade, fd.liste_type, f.pakrevd, ov.verdi, ov.verdi_bigint
                from oppgavefelt_verdi ov 
                inner join oppgavefelt f on ov.oppgavefelt_id = f.id 
                inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id 
                inner join omrade o on fd.omrade_id = o.id 
                where ov.oppgave_id IN ($placeholders)
                order by ov.oppgave_id, fd.ekstern_id
                """.trimIndent(),
                params
            ).map { row ->
                row.long("oppgave_id") to mapOppgavefelt(row)
            }.asList
        ).groupBy({ it.first }, { it.second })
    }

    private fun mapOppgavefelt(row: Row) = Oppgavefelt(
        eksternId = row.string("ekstern_id"),
        område = row.string("omrade"),
        listetype = row.boolean("liste_type"),
        påkrevd = row.boolean("pakrevd"),
        verdi = row.string("verdi"),
        verdiBigInt = row.longOrNull("verdi_bigint"),
    )
}