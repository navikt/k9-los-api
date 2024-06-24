package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.db.util.InClauseHjelper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class OppgaveRepository(
    private val oppgavetypeRepository: OppgavetypeRepository
) {
    private val log: Logger = LoggerFactory.getLogger("OppgaveRepository")

    fun hentNyesteOppgaveForEksternId(tx: TransactionalSession, kildeområde: String, eksternId: String, now: LocalDateTime = LocalDateTime.now()): Oppgave {
        return hentNyesteOppgaveForEksternIdHvisFinnes(tx, kildeområde, eksternId, now) ?: throw IllegalStateException("Fant ikke oppgave med kilde $kildeområde og eksternId $eksternId")
    }

    fun hentNyesteOppgaveForEksternIdHvisFinnes(tx: TransactionalSession, kildeområde: String, eksternId: String, now: LocalDateTime = LocalDateTime.now()): Oppgave? {
        val queryString = """
                select * 
                from oppgave_v3_aktiv ov
                where ov.kildeomrade = :kildeomrade 
                 AND ov.ekstern_id = :eksternId 
            """.trimIndent()

        val oppgave = tx.run(
            queryOf(
                queryString,
                mapOf(
                    "kildeomrade" to kildeområde,
                    "eksternId" to eksternId
                )
            ).map { row -> mapAktivOppgave(row, now, tx) }.asSingle
        )
        return oppgave
    }

    fun hentAlleÅpneOppgaverForReservasjonsnøkkel(tx: TransactionalSession, reservasjonsnøkkel: String, now: LocalDateTime = LocalDateTime.now()) : List<Oppgave> {
        return hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, listOf(reservasjonsnøkkel), now)
    }

    fun hentAlleÅpneOppgaverForReservasjonsnøkkel(tx: TransactionalSession, reservasjonsnøkler: List<String>, now: LocalDateTime = LocalDateTime.now()) : List<Oppgave> {
        val queryString = """
                select *
                from oppgave_v3_aktiv ov 
                where reservasjonsnokkel in (${InClauseHjelper.tilParameternavn(reservasjonsnøkler, "n")})
                and status in ('VENTER', 'AAPEN')
            """.trimIndent()

        val oppgaver = tx.run(
            queryOf(
                queryString,
                InClauseHjelper.parameternavnTilVerdierMap(reservasjonsnøkler, "n")
            ).map { row ->
                mapAktivOppgave(row, now, tx)
            }.asList
        )

        return oppgaver
    }

    fun hentAktivOppgaveForId(tx: TransactionalSession, id: Long, now: LocalDateTime = LocalDateTime.now()): Oppgave {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3_aktiv ov
                where ov.id = :id
            """.trimIndent(),
                mapOf("id" to id)
            ).map { row ->
                mapAktivOppgave(row, now, tx)
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $id")

        return oppgave
    }

    private fun mapAktivOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Oppgave {
        val kildeområde = row.string("kildeomrade")
        val oppgaveTypeId = row.long("oppgavetype_id")
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx)
        val oppgavefelter = hentOppgavefelterAktiv(tx, row.long("id"))
        return Oppgave(
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            oppgavetype = oppgavetype,
            status = row.string("status"),
            endretTidspunkt = row.localDateTime("endret_tidspunkt"),
            kildeområde = row.string("kildeomrade"),
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
            versjon = row.int("versjon")
        ).fyllDefaultverdier().utledTransienteFelter(now)
    }

    private fun hentOppgavefelterAktiv(tx: TransactionalSession, oppgaveId: Long): List<Oppgavefelt> {
        return tx.run(
            queryOf(
                """
                select fd.ekstern_id as ekstern_id, o.ekstern_id as omrade, fd.liste_type, f.pakrevd, ov.verdi
                from oppgavefelt_verdi_aktiv ov 
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

    fun hentOppgaverMedStatusOgPepCacheEldreEnn(
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        antall: Int = 1,
        status: Set<Oppgavestatus>,
        tx: TransactionalSession
    ): List<Oppgave> {
        return tx.run(
            queryOf(
                """
                    SELECT o.*
                    FROM oppgave_v3_aktiv o 
                    LEFT JOIN OPPGAVE_PEP_CACHE opc ON (
                        o.kildeomrade = opc.kildeomrade AND o.ekstern_id = opc.ekstern_id
                    )
                    WHERE o.status IN (${InClauseHjelper.tilParameternavnMedCast(status, "status", "oppgavestatus")})
                    AND (opc.oppdatert is null OR opc.oppdatert < :grense)
                    ORDER BY opc.oppdatert NULLS FIRST
                    LIMIT :limit
                """.trimIndent(),
                mapOf(
                    "grense" to tidspunkt,
                    "limit" to antall
                ) + InClauseHjelper.parameternavnTilVerdierMap(status.map { it.kode }, "status")
            ).map { row -> mapAktivOppgave(row, tidspunkt, tx) }.asList
        )
    }
}