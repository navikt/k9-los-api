package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import java.time.LocalDateTime
import no.nav.k9.los.spi.felter.HentVerdiInput

class OppgaveRepository(
    private val oppgavetypeRepository: OppgavetypeRepository
) {

    fun hentNyesteOppgaveForEksternId(tx: TransactionalSession, kildeområde: String, eksternId: String, now: LocalDateTime = LocalDateTime.now()): Oppgave {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3 ov
                where ov.kildeomrade = :kildeomrade AND ov.ekstern_id = :eksternId 
                and ov.versjon = (select max(versjon) from oppgave_v3 ov2 where ov2.ekstern_id = :eksternId)
            """.trimIndent(),
                mapOf(
                    "kildeomrade" to kildeområde,
                    "eksternId" to eksternId
                )
            ).map { row -> row.mapOppgave(tx) }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med kilde $kildeområde og eksternId $eksternId")

        return oppgave.fyllDefaultverdier().utledTransienteFelter(now)
    }

    private fun Row.mapOppgave(tx: TransactionalSession): Oppgave {
        val oppgaveTypeId = long("oppgavetype_id")
        val kildeområde = string("kildeomrade")
        return Oppgave(
            eksternId = string("ekstern_id"),
            eksternVersjon = string("ekstern_versjon"),
            oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx),
            status = string("status"),
            endretTidspunkt = localDateTime("endret_tidspunkt"),
            kildeområde = kildeområde,
            felter = hentOppgavefelter(tx, long("id")),
            versjon = int("versjon")
        )
    }

    fun hentOppgaveForId(tx: TransactionalSession, id: Long, now: LocalDateTime = LocalDateTime.now()): Oppgave {
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

        return oppgave.fyllDefaultverdier().utledTransienteFelter(now)
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
                    FROM oppgave_v3 o 
                    LEFT JOIN OPPGAVE_PEP_CACHE opc ON (
                        o.kildeomrade = opc.kildeomrade AND o.ekstern_id = opc.ekstern_id
                    )
                    WHERE o.aktiv is true AND o.status IN ('${status.joinToString("','")}')
                    AND (opc.oppdatert is null OR opc.oppdatert < :grense)
                    ORDER BY opc.oppdatert
                    LIMIT :limit
                """.trimIndent(),
                mapOf(
                    "grense" to tidspunkt,
                    "limit" to antall
                )
            ).map { row -> row.mapOppgave(tx) }.asList
        )
    }
}