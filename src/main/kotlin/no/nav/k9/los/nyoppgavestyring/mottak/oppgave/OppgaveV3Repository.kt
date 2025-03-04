package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveV3Repository(
    private val dataSource: DataSource,
    private val oppgavetypeRepository: OppgavetypeRepository,
) {

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

    fun nyOppgaveversjon(oppgave: OppgaveV3, tx: TransactionalSession) {
        val (eksisterendeId, eksisterendeVersjon) = hentOppgaveIdOgHøyesteInternversjon(
            tx,
            oppgave.eksternId,
            oppgave.oppgavetype.eksternId,
            oppgave.oppgavetype.område.eksternId
        )

        eksisterendeId?.let {
            deaktiverVersjon(eksisterendeId, oppgave.endretTidspunkt, tx)
            deaktiverOppgavefelter(eksisterendeId, tx)
        }

        val nyVersjon = eksisterendeVersjon?.plus(1) ?: 0

        val oppgaveId = nyOppgaveversjon(oppgave, nyVersjon, tx)
        lagreFeltverdier(oppgaveId, oppgave, tx)

        val ignorerForKøer = gjelderFRISINN(oppgave)
        if (ignorerForKøer) {
            log.info("Oppdaterer ikke aktiv oppgave, da hendelsen gjaldt frisinn for oppgaveId ${oppgave.eksternId}")
        } else {
            AktivOppgaveRepository.ajourholdAktivOppgave(oppgave, nyVersjon, tx)
        }
    }

    fun hentOppgaveversjon(
        område: Område,
        eksternId: String,
        eksternVersjon: String,
        tx: TransactionalSession
    ): OppgaveV3 {
        return tx.run(
            queryOf(
                """
                    select o.*
                    from oppgave_v3 o
                        inner join oppgavetype ot on o.oppgavetype_id = ot.id
                        inner join omrade omr on ot.omrade_id = omr.id
                    where o.ekstern_id = :oppgave_ekstern_id
                    and o.ekstern_versjon = :oppgave_ekstern_versjon 
                    and omr.ekstern_id = :omrade_ekstern_id
                """.trimIndent(),
                mapOf(
                    "oppgave_ekstern_id" to eksternId,
                    "oppgave_ekstern_versjon" to eksternVersjon,
                    "omrade_ekstern_id" to område.eksternId
                )
            ).map { row ->
                OppgaveV3(
                    id = OppgaveId(row.long("id")),
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetypeRepository.hentOppgavetype(
                        område = område.eksternId,
                        row.long("oppgavetype_id"),
                        tx
                    ),
                    status = Oppgavestatus.valueOf(row.string("status")),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    reservasjonsnøkkel = row.stringOrNull("reservasjonsnokkel") ?: "mangler_historikkvask",
                    aktiv = row.boolean("aktiv"),
                    felter = hentFeltverdier(
                        OppgaveId(row.long("id")),
                        oppgavetypeRepository.hentOppgavetype(
                            område = område.eksternId,
                            row.long("oppgavetype_id"),
                            tx
                        ),
                        tx
                    )
                )
            }.asSingle
        )
            ?: throw IllegalArgumentException("Fant ikke oppgave med ekstern_id: ${eksternId}, ekstern_versjon: ${eksternVersjon} og område: ${område.eksternId}")
    }

    fun hentOppgaveversjonenFør(
        eksternId: String,
        internVersjon: Long,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): OppgaveV3? {
        return tx.run(
            queryOf(
                """
                    select *
                    from oppgave_v3 ov 
                    where ekstern_id = :eksternId
                    and versjon = :internVersjon
                """.trimIndent(),
                mapOf(
                    "eksternId" to eksternId,
                    "internVersjon" to internVersjon - 1
                )
            ).map { row ->
                OppgaveV3(
                    id = OppgaveId(row.long("id")),
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetype,
                    status = Oppgavestatus.valueOf(row.string("status")),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    reservasjonsnøkkel = row.stringOrNull("reservasjonsnokkel") ?: "mangler_historikkvask",
                    aktiv = row.boolean("aktiv"),
                    felter = hentFeltverdier(OppgaveId(row.long("id")), oppgavetype, tx)
                )
            }.asSingle
        )
    }

    fun hentAktivOppgave(eksternId: String, oppgavetype: Oppgavetype, tx: TransactionalSession): OppgaveV3? {
        return tx.run(
            queryOf(
                """
                    select * from oppgave_v3 where ekstern_id = :eksternId and aktiv = true
                """.trimIndent(), mapOf("eksternId" to eksternId)
            ).map { row ->
                OppgaveV3(
                    id = OppgaveId(row.long("id")),
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetype,
                    status = Oppgavestatus.valueOf(row.string("status")),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    reservasjonsnøkkel = row.stringOrNull("reservasjonsnokkel") ?: "mangler_historikkvask",
                    aktiv = row.boolean("aktiv"),
                    felter = hentFeltverdier(OppgaveId(row.long("id")), oppgavetype, tx)
                )
            }.asSingle
        )
    }

    fun hentEksternIdForOppgaverMedStatus(
        oppgavetype: Oppgavetype,
        område: Område,
        oppgavestatus: Oppgavestatus,
        tx: TransactionalSession
    ): List<String> {
        return tx.run(
            queryOf(
                """
                    select ov.ekstern_id as ekstern_id
                    from oppgave_v3 ov
                        inner join oppgavetype ot on ov.oppgavetype_id = ot.id and ot.ekstern_id = :oppgavetype
                        inner join omrade o on ot.omrade_id = o.id and o.ekstern_id = :omrade
                    where ov.status = :oppgavestatus
                    and ov.aktiv = true
                """.trimIndent(),
                mapOf(
                    "oppgavetype" to oppgavetype.eksternId,
                    "omrade" to område.eksternId,
                    "oppgavestatus" to oppgavestatus.kode
                )
            ).map { row ->
                row.string("ekstern_id")
            }.asList
        )
    }

    fun oppdaterReservasjonsnøkkelStatusOgEksternVersjon(
        eksternId: String,
        eksternVersjon: String,
        status: Oppgavestatus,
        internVersjon: Long,
        reservasjonsnokkel: String,
        tx: TransactionalSession
    ) {
        tx.run(
            queryOf(
                """
                    update oppgave_v3 
                    set reservasjonsnokkel = :reservasjonsnokkel, ekstern_versjon = :eksternVersjon, status = :status
                    where ekstern_id = :eksternId 
                    and versjon = :internVersjon
                """.trimIndent(),
                mapOf(
                    "reservasjonsnokkel" to reservasjonsnokkel,
                    "eksternVersjon" to eksternVersjon,
                    "status" to status.kode,
                    "eksternId" to eksternId,
                    "internVersjon" to internVersjon
                )
            ).asUpdateAndReturnGeneratedKey
        )
    }

    @VisibleForTesting
    fun nyOppgaveversjon(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession): OppgaveId {
        return OppgaveId(tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    insert into oppgave_v3(ekstern_id, ekstern_versjon, oppgavetype_id, status, versjon, aktiv, kildeomrade, endret_tidspunkt, reservasjonsnokkel)
                    values(:eksternId, :eksternVersjon, :oppgavetypeId, :status, :versjon, :aktiv, :kildeomrade, :endretTidspunkt, :reservasjonsnokkel)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "eksternVersjon" to oppgave.eksternVersjon,
                    "oppgavetypeId" to oppgave.oppgavetype.id,
                    "status" to oppgave.status.toString(),
                    "endretTidspunkt" to oppgave.endretTidspunkt,
                    "versjon" to nyVersjon,
                    "aktiv" to true,
                    "kildeomrade" to oppgave.kildeområde,
                    "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
                )
            )
        )!!)
    }

    private fun hentFeltverdier(
        oppgaveId: OppgaveId,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi where oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId.id)
            ).map { row ->
                OppgaveFeltverdi(
                    id = row.long("id"),
                    oppgavefelt = oppgavetype.oppgavefelter.first { oppgavefelt ->
                        oppgavefelt.id == row.long("oppgavefelt_id")
                    },
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint")
                )
            }.asList
        )
    }

    @VisibleForTesting
    fun lagreFeltverdier(
        oppgaveId: OppgaveId,
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        tx.batchPreparedNamedStatement("""
            insert into oppgavefelt_verdi(oppgave_id, oppgavefelt_id, omrade_ekstern_id, oppgavetype_ekstern_id, feltdefinisjon_ekstern_id, verdi, verdi_bigint, aktiv)
                    VALUES (:oppgaveId, :oppgavefeltId, :omradeEksternId, :oppgavetypeEksternId, :feltdefinisjonEksternId, :verdi, :verdi_bigint, :aktiv)
        """.trimIndent(),
            oppgave.felter.map { feltverdi ->
                mapOf(
                    "oppgaveId" to oppgaveId.id,
                    "oppgavefeltId" to feltverdi.oppgavefelt.id,
                    "omradeEksternId" to oppgave.oppgavetype.område.eksternId,
                    "oppgavetypeEksternId" to oppgave.oppgavetype.eksternId,
                    "feltdefinisjonEksternId" to feltverdi.oppgavefelt.feltDefinisjon.eksternId,
                    "verdi" to feltverdi.verdi,
                    "verdi_bigint" to feltverdi.verdiBigInt,
                    "aktiv" to oppgave.aktiv
                )
            }
        )
    }

    fun lagreFeltverdierForDatavask(
        eksternId: String,
        internVersjon: Long,
        aktiv: Boolean,
        oppgaveFeltverdier: List<OppgaveFeltverdi>,
        tx: TransactionalSession
    ) {
        tx.batchPreparedNamedStatement("""
            INSERT INTO oppgavefelt_verdi(oppgave_id, oppgavefelt_id, omrade_ekstern_id, oppgavetype_ekstern_id, feltdefinisjon_ekstern_id, verdi, verdi_bigint, aktiv)
            VALUES (
                (
                    SELECT id 
                    FROM oppgave_v3
                    WHERE ekstern_id = :ekstern_id
                    AND versjon = :intern_versjon
                ),
                :oppgavefelt_id,
                :omrade_ekstern_id,
                (
                    SELECT ot.ekstern_id
                    FROM oppgavetype ot
                    INNER JOIN oppgave_v3 o on ot.id = o.oppgavetype_id
                    WHERE o.ekstern_id = :ekstern_id
                    AND o.versjon = :intern_versjon
                ),
                :feltdefinisjon_ekstern_id,
                :verdi,
                :verdi_bigint,
                :aktiv
            )
        """.trimIndent(),
            oppgaveFeltverdier.map { feltverdi ->
                mapOf(
                    "ekstern_id" to eksternId,
                    "intern_versjon" to internVersjon,
                    "oppgavefelt_id" to feltverdi.oppgavefelt.id,
                    "omrade_ekstern_id" to feltverdi.oppgavefelt.feltDefinisjon.område.eksternId,
                    "feltdefinisjon_ekstern_id" to feltverdi.oppgavefelt.feltDefinisjon.eksternId,
                    "verdi" to feltverdi.verdi,
                    "verdi_bigint" to feltverdi.verdiBigInt,
                    "aktiv" to aktiv
                )
            }
        )
    }

    fun slettFeltverdier(
        eksternId: String,
        internVersjon: Long,
        tx: TransactionalSession
    ) {
        tx.update(
            queryOf(
                """
                    delete 
                    from oppgavefelt_verdi 
                    where oppgavefelt_verdi.id in (
                        select ov.id
                        from oppgavefelt_verdi ov
                        inner join oppgave_v3 o on ov.oppgave_id = o.id
                        where o.ekstern_id = :ekstern_id
                          and o.versjon = :intern_versjon
                    )
                    """.trimIndent(),
                mapOf(
                    "ekstern_id" to eksternId,
                    "intern_versjon" to internVersjon
                )
            )
        )
    }

    fun hentOppgaveIdOgHøyesteInternversjon(
        tx: TransactionalSession,
        oppgaveEksternId: String,
        oppgaveTypeEksternId: String,
        områdeEksternId: String
    ): Pair<OppgaveId?, Long?> {
        return tx.run(
            queryOf(
                """
                select versjon, o.id
                from oppgave_v3 o
                inner join oppgavetype ot on o.oppgavetype_id = ot.id 
                inner join omrade om on ot.omrade_id = om.id 
                where o.ekstern_id = :ekstern_id
                  and ot.ekstern_id = :oppgavetype_ekstern_id
                  and om.ekstern_id = :omrade_ekstern_id
                  and versjon = 
                    (select max(versjon)
                     from oppgave_v3 oi
                     where oi.ekstern_id = o.ekstern_id
                       and oi.oppgavetype_id = o.oppgavetype_id)
                """.trimIndent(),
                mapOf(
                    "ekstern_id" to oppgaveEksternId,
                    "oppgavetype_ekstern_id" to oppgaveTypeEksternId,
                    "omrade_ekstern_id" to områdeEksternId
                )
            ).map { row ->
                Pair(
                    OppgaveId(row.long("id")),
                    row.long("versjon")
                )
            }.asSingle
        ) ?: Pair(null, null)
    }

    @VisibleForTesting
    fun deaktiverVersjon(eksisterendeId: OppgaveId, deaktivertTidspunkt: LocalDateTime, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                update oppgave_v3 set aktiv = false, deaktivert_tidspunkt = :deaktivertTidspunkt where id = :id
            """.trimIndent(),
                mapOf(
                    "id" to eksisterendeId.id,
                    "deaktivertTidspunkt" to deaktivertTidspunkt
                )
            ).asUpdate
        )
    }

    @VisibleForTesting
    fun deaktiverOppgavefelter(oppgaveId: OppgaveId, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                update oppgavefelt_verdi
                set aktiv = false
                where oppgave_id = :oppgave_id 
            """.trimIndent(),
                mapOf(
                    "oppgave_id" to oppgaveId.id
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