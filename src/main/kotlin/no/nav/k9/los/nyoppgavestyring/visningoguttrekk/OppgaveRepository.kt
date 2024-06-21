package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.spi.felter.HentVerdiInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class OppgaveRepository(
    private val oppgavetypeRepository: OppgavetypeRepository
) {
    private val log: Logger = LoggerFactory.getLogger("OppgaveRepository")
    fun hentNyesteOppgaveForEksternId(tx: TransactionalSession, kildeområde: String, eksternId: String, now: LocalDateTime = LocalDateTime.now()): Oppgave {
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
        ) ?: throw IllegalStateException("Fant ikke oppgave med kilde $kildeområde og eksternId $eksternId")

        return oppgave
    }

    fun hentAlleÅpneOppgaverForReservasjonsnøkkel(tx: TransactionalSession, reservasjonsnøkkel: String, now: LocalDateTime = LocalDateTime.now()) : List<Oppgave> {
        return hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, listOf(reservasjonsnøkkel), now)
    }

    fun hentAlleÅpneOppgaverForReservasjonsnøkkel(tx: TransactionalSession, reservasjonsnøkler: List<String>, now: LocalDateTime = LocalDateTime.now()) : List<Oppgave> {
        val queryString = """
                select *
                from oppgave_v3_aktiv ov 
                where reservasjonsnokkel in ('${reservasjonsnøkler.joinToString("','")}')
                and status in ('VENTER', 'AAPEN')
            """.trimIndent()

        val oppgaver = tx.run(
            queryOf(
                queryString
            ).map { row ->
                mapAktivOppgave(row, now, tx)
            }.asList
        )

        return oppgaver
    }

    fun hentOppgaveForId(tx: TransactionalSession, id: Long, now: LocalDateTime = LocalDateTime.now()): Oppgave {
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

    private fun Oppgave.utledTransienteFelter(now: LocalDateTime): Oppgave {
        val utlededeVerdier: List<Oppgavefelt> = this.oppgavetype.oppgavefelter.flatMap { oppgavefelt ->
            oppgavefelt.feltDefinisjon.transientFeltutleder?.let { feltutleder ->
                feltutleder.hentVerdi(
                    HentVerdiInput(
                        now,
                        this,
                        oppgavefelt.feltDefinisjon.område.eksternId,
                        oppgavefelt.feltDefinisjon.eksternId
                    )
                ).map { verdi ->
                    Oppgavefelt(
                        eksternId = oppgavefelt.feltDefinisjon.eksternId,
                        område = oppgavefelt.feltDefinisjon.område.eksternId,
                        listetype = oppgavefelt.feltDefinisjon.listetype,
                        påkrevd = false,
                        verdi = verdi
                    )
                }
            } ?: listOf()
        }
        return copy(felter = felter.plus(utlededeVerdier))
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

    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Oppgave {
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
            kildeområde = row.string("kildeomrade"),
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
            versjon = row.int("versjon")
        ).fyllDefaultverdier().utledTransienteFelter(now)
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
                    WHERE o.status IN ('${status.joinToString("','")}')
                    AND (opc.oppdatert is null OR opc.oppdatert < :grense)
                    ORDER BY opc.oppdatert NULLS FIRST
                    LIMIT :limit
                """.trimIndent(),
                mapOf(
                    "grense" to tidspunkt,
                    "limit" to antall
                )
            ).map { row -> mapAktivOppgave(row, tidspunkt, tx) }.asList
        )
    }

    fun hentOppgaveTidsserie(
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        områdeEksternId: String,
        oppgaveTypeEksternId: String,
        oppgaveEksternId: String,
        tx: TransactionalSession
    ): List<Oppgave> {
        return tx.run(
            queryOf(
                """
                    select *
                    from oppgave_v3 o
                    	inner join oppgavetype ot on o.oppgavetype_id = ot.id 
                    	inner join omrade omr on ot.omrade_id = omr.id 
                    where omr.ekstern_id = :omrade
                    and ot.ekstern_id = :oppgavetype
                    and o.ekstern_id = :oppgaveEksternId
                    order by o.versjon asc
                """.trimIndent(),
                mapOf(
                    "omrade" to områdeEksternId,
                    "oppgavetype" to oppgaveTypeEksternId,
                    "oppgaveEksternId" to oppgaveEksternId,
                )
            ).map { row -> mapOppgave(row, tidspunkt, tx) }.asList
        )
    }


}