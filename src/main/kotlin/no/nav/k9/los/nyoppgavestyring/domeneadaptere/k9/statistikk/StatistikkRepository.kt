package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import kotliquery.*
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.Oppgave
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.Oppgavefelt
import java.time.LocalDateTime
import javax.sql.DataSource

data class DvhPendingPerOppgavetypeDto(
    val oppgavetype: String,
    val antall: Long,
)


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
                        from oppgave_v3_dvh_pending p
                        join oppgave_v3 ov
                            on ov.ekstern_id = p.ekstern_id
                           and ov.ekstern_versjon = p.ekstern_versjon
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
                        where p.oppgavetype_ekstern_id in ('k9sak', 'k9klage')
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
                        delete from oppgave_v3_dvh_pending
                        where (ekstern_id, ekstern_versjon) in (
                            select ekstern_id, ekstern_versjon from oppgave_v3 where id = :id
                        )
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
                    delete from oppgave_v3_dvh_pending
                    where (ekstern_id, ekstern_versjon) in (
                        select ekstern_id, ekstern_versjon from oppgave_v3 where id = :id
                    )
                """.trimIndent(),
                mapOf("id" to id)
            ).asUpdate
        )
    }


    fun fjernSendtMarkering(oppgave: OppgaveNøkkelDto, tx: TransactionalSession) {
        // Legg alle versjoner av oppgaven tilbake som pending (resend fra start)
        tx.run(
            queryOf(
                """
                insert into oppgave_v3_dvh_pending (ekstern_id, ekstern_versjon, oppgavetype_ekstern_id)
                select ekstern_id, ekstern_versjon, oppgavetype_ekstern_id
                from oppgave_v3
                where ekstern_id = :id
                  and oppgavetype_ekstern_id in ('k9sak', 'k9klage')
                on conflict (ekstern_id, ekstern_versjon) do nothing
                """.trimIndent(),
                mapOf("id" to oppgave.oppgaveEksternId)
            ).asUpdate
        )
    }

fun fjernSendtMarkering(oppgavetype: String? = null) {
    if (oppgavetype != null && oppgavetype !in DVH_OPPGAVETYPER) return
    using(sessionOf(dataSource)) {
        if (oppgavetype != null) {
                it.run(
                    queryOf(
                        """
                        insert into oppgave_v3_dvh_pending (ekstern_id, ekstern_versjon, oppgavetype_ekstern_id)
                        select ov.ekstern_id, ov.ekstern_versjon, ov.oppgavetype_ekstern_id
                        from oppgave_v3 ov
                        where ov.oppgavetype_ekstern_id = :oppgavetype
                        on conflict (ekstern_id, ekstern_versjon) do nothing
                        """.trimIndent(),
                        mapOf("oppgavetype" to oppgavetype)
                    ).asUpdate
                )
            } else {
                // Repopuler pending for alle relevante oppgavetyper
                it.run(
                    queryOf(
                        """
                        insert into oppgave_v3_dvh_pending (ekstern_id, ekstern_versjon, oppgavetype_ekstern_id)
                        select ov.ekstern_id, ov.ekstern_versjon, ov.oppgavetype_ekstern_id
                        from oppgave_v3 ov
                        where ov.oppgavetype_ekstern_id in ('k9sak', 'k9klage')
                        on conflict (ekstern_id, ekstern_versjon) do nothing
                        """.trimIndent()
                    ).asUpdate
                )
            }
        }
    }

    /**
     * Dual-write: registrerer en ny oppgaveversjon i pending-tabellen så snart den er lagret
     * i oppgave_v3, men før den er sendt til DVH. Kalles fra EventTilOppgaveAdapter.
     * Kun relevant for oppgavetyper som sendes til DVH (k9sak, k9klage).
     */
    fun bestillDvhSending(
        eksternId: String,
        eksternVersjon: String,
        oppgavetypeEksternId: String,
        tx: TransactionalSession,
    ) {
        if (oppgavetypeEksternId !in DVH_OPPGAVETYPER) return
        tx.run(
            queryOf(
                """
                insert into oppgave_v3_dvh_pending (ekstern_id, ekstern_versjon, oppgavetype_ekstern_id)
                values (:eksternId, :eksternVersjon, :oppgavetypeEksternId)
                on conflict (ekstern_id, ekstern_versjon) do nothing
                """.trimIndent(),
                mapOf(
                    "eksternId" to eksternId,
                    "eksternVersjon" to eksternVersjon,
                    "oppgavetypeEksternId" to oppgavetypeEksternId,
                )
            ).asUpdate
        )
    }

    companion object {
        /** Oppgavetyper hvis versjoner spores i oppgave_v3_dvh_pending for sending til DVH. */
        private val DVH_OPPGAVETYPER = setOf("k9sak", "k9klage")
    }

    /**
     * Populerer oppgave_v3_dvh_pending fra oppgave_v3.
     * Backfill/requeue-operasjon som ikke skal kjøres i normal drift, siden allerede
     * sendte oppgaveversjoner kan bli lagt til i pending på nytt.
     * Returnerer antall rader lagt til.
     */
    fun populerPendingFraOppgaveV3(): Int {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    insert into oppgave_v3_dvh_pending (ekstern_id, ekstern_versjon, oppgavetype_ekstern_id)
                    select ov.ekstern_id, ov.ekstern_versjon, ov.oppgavetype_ekstern_id
                    from oppgave_v3 ov
                    where ov.oppgavetype_ekstern_id in ('k9sak', 'k9klage')
                    on conflict (ekstern_id, ekstern_versjon) do nothing
                    """.trimIndent()
                ).asUpdate
            )
        }
    }

    fun hentPendingPerOppgavetype(): List<DvhPendingPerOppgavetypeDto> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select oppgavetype_ekstern_id, count(*) as antall
                    from oppgave_v3_dvh_pending
                    group by oppgavetype_ekstern_id
                    order by oppgavetype_ekstern_id
                    """.trimIndent()
                ).map { row ->
                    DvhPendingPerOppgavetypeDto(
                        oppgavetype = row.string("oppgavetype_ekstern_id"),
                        antall = row.long("antall"),
                    )
                }.asList
            )
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
                mapOppgave(row, now, tx)
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

        return oppgave
    }

    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Pair<Oppgave, Int> {
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
}