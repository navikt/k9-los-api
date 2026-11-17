package no.nav.k9.los.domeneadaptere.statistikk

import kotliquery.*
import no.nav.k9.los.domeneadaptere.eventlager.Oppgavetype
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import no.nav.k9.los.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.Oppgavefelt
import java.time.LocalDateTime
import javax.sql.DataSource

data class DvhPendingPerOppgavetypeDto(
    val oppgavetype: String,
    val antall: Long,
)

data class DvhOppgaveversjon(
    val oppgavetype: Oppgavetype,
    val eksternId: String,
    val eksternVersjon: String,
)


class StatistikkRepository(
    private val dataSource: DataSource,
    private val oppgavetypeRepository: OppgavetypeRepository
) {

    fun hentOppgaverSomIkkeErSendt(oppgavetype: Oppgavetype): List<DvhOppgaveversjon> {
        require(oppgavetype in DVH_OPPGAVETYPER.getValue(oppgavetype.område)) {
            "Oppgavetype ${oppgavetype.kode} er ikke konfigurert for DVH-sending i område ${oppgavetype.område}"
        }
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select p.ekstern_id, p.ekstern_versjon
                        from oppgave_v3_dvh_pending p
                        join oppgave_v3 ov
                            on ov.ekstern_id = p.ekstern_id
                           and ov.ekstern_versjon = p.ekstern_versjon
                           and ov.oppgavetype_ekstern_id = p.oppgavetype_ekstern_id
                           and ov.kildeomrade = p.omrade_ekstern_id
                        left join lateral (
                            select ofv.verdi as saksnummer
                            from oppgavefelt_verdi ofv
                            join oppgavefelt f
                                on ofv.oppgavefelt_id = f.id
                            join feltdefinisjon fd
                                on f.feltdefinisjon_id = fd.id
                            join omrade om
                                on fd.omrade_id = om.id
                            where ofv.oppgave_id = ov.id
                                and fd.ekstern_id = 'saksnummer'
                                and om.ekstern_id = :omrade
                        ) saksnummer on true
                        where p.omrade_ekstern_id = :omrade
                          and p.oppgavetype_ekstern_id = :oppgavetype
                          and not exists (
                              select 1
                              from event_historikkvask_bestilt hb
                              join event_nokkel en on en.id = hb.event_nokkel_id
                              where en.ekstern_id = p.ekstern_id
                                and en.fagsystem = :fagsystem
                          )
                        order by saksnummer.saksnummer nulls last, ov.ekstern_id, ov.ekstern_versjon, ov.oppgavetype_ekstern_id
                    """.trimIndent(),
                    mapOf(
                        "omrade" to oppgavetype.område.eksternId,
                        "oppgavetype" to oppgavetype.kode,
                        "fagsystem" to oppgavetype.fagsystem.kode
                    )
                ).map { row ->
                    DvhOppgaveversjon(oppgavetype, row.string("ekstern_id"), row.string("ekstern_versjon"))
                }.asList
            )
        }
    }

    fun kvitterSending(oppgaveversjon: DvhOppgaveversjon) {
        using(sessionOf(dataSource)) {
            it.run(kvitterSendingQuery(oppgaveversjon).asUpdate)
        }
    }

    fun kvitterSending(tx: TransactionalSession, oppgaveversjon: DvhOppgaveversjon) {
        tx.run(kvitterSendingQuery(oppgaveversjon).asUpdate)
    }

    private fun kvitterSendingQuery(oppgaveversjon: DvhOppgaveversjon) = queryOf(
        """
            delete from oppgave_v3_dvh_pending p
            where p.omrade_ekstern_id = :omrade
              and p.oppgavetype_ekstern_id = :oppgavetype
              and p.ekstern_id = :eksternId
              and p.ekstern_versjon = :eksternVersjon
        """.trimIndent(),
        oppgaveversjon.parametre()
    )

    private fun DvhOppgaveversjon.parametre() = mapOf(
        "omrade" to oppgavetype.område.eksternId,
        "oppgavetype" to oppgavetype.kode,
        "fagsystem" to oppgavetype.fagsystem.kode,
        "eksternId" to eksternId,
        "eksternVersjon" to eksternVersjon,
    )

    fun bestillDvhSending(oppgavetype: String? = null) {
        val oppgavetyperPerOmråde = if (oppgavetype != null) {
            val type = DVH_OPPGAVETYPER.values.flatten().find { it.kode == oppgavetype } ?: return
            mapOf(type.område to setOf(type))
        } else {
            DVH_OPPGAVETYPER
        }
        using(sessionOf(dataSource)) { session ->
            oppgavetyperPerOmråde.forEach { (område, oppgavetyper) ->
                session.run(leggTilPendingFraOppgaveV3Query(område, oppgavetyper).asUpdate)
            }
        }
    }

    /**
     * Dual-write: registrerer en ny oppgaveversjon i pending-tabellen så snart den er lagret
     * i oppgave_v3, men før den er sendt til DVH. Kalles fra EventTilOppgaveAdapter.
     * Kun relevant for oppgavetyper som sendes til DVH, se [DVH_OPPGAVETYPER].
     */
    fun bestillDvhSending(
        område: Områder,
        eksternId: String,
        eksternVersjon: String,
        oppgavetypeEksternId: String,
        tx: TransactionalSession,
    ) {
        if (DVH_OPPGAVETYPER[område].orEmpty().none { it.kode == oppgavetypeEksternId }) return
        tx.run(
            queryOf(
                """
                insert into oppgave_v3_dvh_pending (omrade_ekstern_id, oppgavetype_ekstern_id, ekstern_id, ekstern_versjon)
                values (:omrade, :oppgavetypeEksternId, :eksternId, :eksternVersjon)
                on conflict do nothing
                """.trimIndent(),
                mapOf(
                    "omrade" to område.eksternId,
                    "eksternId" to eksternId,
                    "eksternVersjon" to eksternVersjon,
                    "oppgavetypeEksternId" to oppgavetypeEksternId,
                )
            ).asUpdate
        )
    }

    fun bestillDvhSendingForEksternIder(eksternIder: List<String>, tx: TransactionalSession) {
        if (eksternIder.isEmpty()) return
        val oppgavetyper = DVH_OPPGAVETYPER.getValue(Områder.K9).map { it.kode }

        tx.run(
            queryOf(
                """
                insert into oppgave_v3_dvh_pending (omrade_ekstern_id, oppgavetype_ekstern_id, ekstern_id, ekstern_versjon)
                select :omrade, ov.oppgavetype_ekstern_id, ov.ekstern_id, ov.ekstern_versjon
                from oppgave_v3 ov
                where ov.ekstern_id in (${InClauseHjelper.tilParameternavn(eksternIder, "ekstern_id")})
                  and ov.oppgavetype_ekstern_id in (${InClauseHjelper.tilParameternavn(oppgavetyper, "oppgavetype")})
                on conflict do nothing
                """.trimIndent(),
                mapOf("omrade" to Områder.K9.eksternId) +
                    InClauseHjelper.parameternavnTilVerdierMap(eksternIder, "ekstern_id") +
                    InClauseHjelper.parameternavnTilVerdierMap(oppgavetyper, "oppgavetype")
            ).asUpdate
        )
    }

    companion object {
        /** Oppgavetyper per område hvis versjoner spores i oppgave_v3_dvh_pending for sending til DVH. */
        val DVH_OPPGAVETYPER: Map<Områder, Set<Oppgavetype>> = mapOf(
            Områder.K9 to setOf(K9Oppgavetypenavn.SAK, K9Oppgavetypenavn.KLAGE),
            Områder.AKTIVITETSPENGER to setOf(
                AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1,
                AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2,
            ),
            Områder.UNGDOMSPROGRAMYTELSEN to setOf(
                UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR,
            ),
        )
    }

    private fun leggTilPendingFraOppgaveV3Query(område: Områder, oppgavetyper: Set<Oppgavetype>) = queryOf(
        """
        insert into oppgave_v3_dvh_pending (omrade_ekstern_id, oppgavetype_ekstern_id, ekstern_id, ekstern_versjon)
        select :omrade, ov.oppgavetype_ekstern_id, ov.ekstern_id, ov.ekstern_versjon
        from oppgave_v3 ov
        where ov.oppgavetype_ekstern_id in (${InClauseHjelper.tilParameternavn(oppgavetyper, "oppgavetype")})
        on conflict do nothing
        """.trimIndent(),
        mapOf("omrade" to område.eksternId) +
            InClauseHjelper.parameternavnTilVerdierMap(oppgavetyper.map { it.kode }, "oppgavetype")
    )

    /**
     * Populerer oppgave_v3_dvh_pending fra oppgave_v3.
     * Backfill/requeue-operasjon som ikke skal kjøres i normal drift, siden allerede
     * sendte oppgaveversjoner kan bli lagt til i pending på nytt.
     * Returnerer antall rader lagt til.
     */
    fun populerPendingFraOppgaveV3(): Int {
        return using(sessionOf(dataSource)) { session ->
            DVH_OPPGAVETYPER.entries.sumOf { (område, oppgavetyper) ->
                session.run(leggTilPendingFraOppgaveV3Query(område, oppgavetyper).asUpdate)
            }
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

    fun hentOppgaveForSending(
        tx: TransactionalSession,
        oppgaveversjon: DvhOppgaveversjon,
        now: LocalDateTime = LocalDateTime.now(),
    ): Pair<Oppgave, Int>? {
        return tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.kildeomrade = :omrade
                  and ov.oppgavetype_ekstern_id = :oppgavetype
                  and ov.ekstern_id = :eksternId
                  and ov.ekstern_versjon = :eksternVersjon
                  and exists (
                      select 1
                      from oppgave_v3_dvh_pending p
                      where p.omrade_ekstern_id = :omrade
                        and p.oppgavetype_ekstern_id = :oppgavetype
                        and p.ekstern_id = :eksternId
                        and p.ekstern_versjon = :eksternVersjon
                  )
                  and not exists (
                      select 1
                      from event_historikkvask_bestilt hb
                      join event_nokkel en on en.id = hb.event_nokkel_id
                      where en.fagsystem = :fagsystem
                        and en.ekstern_id = :eksternId
                  )
            """.trimIndent(),
                oppgaveversjon.parametre()
            ).map { row ->
                mapOppgave(row, now, tx)
            }.asSingle
        )
    }

    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Pair<Oppgave, Int> {
        val kildeområde = Områder.fraEksternId(row.string("kildeomrade"))
        val oppgaveTypeId = row.long("oppgavetype_id")
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx)
        val oppgavefelter = hentOppgavefelter(tx, row.long("id"))
        return Oppgave(
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            oppgavetype = oppgavetype,
            status = Oppgavestatus.fraKode(row.string("status")),
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
                    område = Områder.fraEksternId(row.string("omrade")),
                    listetype = row.boolean("liste_type"),
                    påkrevd = row.boolean("pakrevd"),
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }
}