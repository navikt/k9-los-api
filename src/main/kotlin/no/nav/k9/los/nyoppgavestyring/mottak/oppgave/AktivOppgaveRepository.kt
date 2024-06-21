package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import org.jetbrains.annotations.VisibleForTesting

object AktivOppgaveRepository {
    fun ajourholdAktivOppgave(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession): Long {
        val oppgaveId =  tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    insert into oppgave_v3_aktiv (ekstern_id, ekstern_versjon, oppgavetype_id, status, versjon, kildeomrade, endret_tidspunkt, reservasjonsnokkel)
                    values(:eksternId, :eksternVersjon, :oppgavetypeId, cast(:status as oppgavestatus), :versjon, :kildeomrade, :endretTidspunkt, :reservasjonsnokkel)
                    on conflict (ekstern_id, kildeomrade)
                    do update set
                        ekstern_versjon = :eksternVersjon,
                        status = cast(:status as oppgavestatus),
                        versjon = :versjon,
                        endret_tidspunkt = :endretTidspunkt,
                        reservasjonsnokkel = :reservasjonsnokkel;
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "eksternVersjon" to oppgave.eksternVersjon,
                    "oppgavetypeId" to oppgave.oppgavetype.id,
                    "status" to oppgave.status.toString(),
                    "endretTidspunkt" to oppgave.endretTidspunkt,
                    "versjon" to nyVersjon,
                    "kildeomrade" to oppgave.kildeområde,
                    "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
                )
            )
        )!!

        oppdaterAktivOppgavefelter(oppgaveId, oppgave, nyVersjon, tx)
        return oppgaveId
    }

    private fun oppdaterAktivOppgavefelter(
        oppgaveId: Long,
        oppgave: OppgaveV3,
        nyVersjon: Long,
        tx: TransactionalSession
    ) {
        check(nyVersjon >= 0) {"Ny versjon må være 0 eller høyere: $nyVersjon"}

        val eksisterendeFelter: List<OppgaveFeltverdi> = if (nyVersjon == 0L) emptyList()
        else hentFeltverdier(oppgaveId, oppgave.oppgavetype, tx)

        val diffResultat = regnUtDiff(eksisterendeFelter, oppgave.felter)
        insertFelter(diffResultat.inserts, oppgave, oppgaveId, tx)
        oppdaterFelter(diffResultat.updates, tx)
        slettFelter(diffResultat.deletes, tx)
    }

    private fun hentFeltverdier(
        oppgaveId: Long,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi_aktiv where oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId)
            ).map { row ->
                OppgaveFeltverdi(
                    id = row.long("id"),
                    oppgavefelt = oppgavetype.oppgavefelter.first { oppgavefelt ->
                        oppgavefelt.id == row.long("oppgavefelt_id")
                    },
                    verdi = row.string("verdi"),
                    aktiv = true,
                    oppgavestatus = row.stringOrNull("oppgavestatus")?.let { Oppgavestatus.fraKode(it) }
                )
            }.asList
        )
    }

    private fun insertFelter(
        inserts: Collection<Verdi>,
        oppgave: OppgaveV3,
        oppgaveId: Long,
        tx: TransactionalSession
    ) {
        if (inserts.isEmpty()){
            return
        }
        tx.batchPreparedNamedStatement("""
                insert into oppgavefelt_verdi_aktiv(oppgave_id, oppgavefelt_id, verdi, oppgavestatus)
                        VALUES (:oppgaveId, :oppgavefeltId, :verdi, cast(:oppgavestatus as oppgavestatus))
            """.trimIndent(),
            inserts.map { verdi ->
                mapOf(
                    "oppgaveId" to oppgaveId,
                    "oppgavefeltId" to verdi.oppgavefeltId,
                    "verdi" to verdi.verdi,
                    "oppgavestatus" to oppgave.status.kode
                )
            }
        )
    }

    private fun oppdaterFelter(updates: Map<Long, Verdi>, tx: TransactionalSession) {
        if (updates.isEmpty()){
            return
        }
        tx.batchPreparedNamedStatement(
            "update oppgavefelt_verdi_aktiv set verdi = :verdi where id = :id",
            updates.map { mapOf("id" to it.key, "verdi" to it.value.verdi ) }.toList()
        )
    }


    private fun slettFelter(deletes: Collection<Long>, tx: TransactionalSession) {
        if (deletes.isEmpty()){
            return
        }
        tx.batchPreparedNamedStatement(
            "delete from oppgavefelt_verdi_aktiv where id = :id",
            deletes.map { mapOf("id" to it ) }.toList()
        )
    }

    @VisibleForTesting
    fun regnUtDiff(eksisterende: List<OppgaveFeltverdi>, nye: List<OppgaveFeltverdi>): DiffResultat {
        val nyeVerdier = nye.map { Verdi(it.verdi, it.oppgavefelt.id!!) }.toSet()
        if (eksisterende.isEmpty()) {
            return DiffResultat(deletes = emptyList(), inserts = nyeVerdier, updates = emptyMap())
        }
        val eksisterendeVerdier = eksisterende.associate {Pair(Verdi(it.verdi, it.oppgavefelt.id!!), it.id!!) }
        val verdierBeggeSteder = eksisterendeVerdier.keys.intersect(nyeVerdier)

        val oppdaterMap: MutableMap<Long, Verdi> = HashMap()
        val kunEksisterende = (eksisterendeVerdier - verdierBeggeSteder).toMutableMap()
        val kunNye = (nyeVerdier - verdierBeggeSteder).toMutableSet()

        for (nyVerdi in ArrayList(kunNye)) {
            val match = kunEksisterende.entries.firstOrNull { it.key.oppgavefeltId == nyVerdi.oppgavefeltId}
            match?.let {
                val id = it.value
                oppdaterMap[id] = nyVerdi
                kunEksisterende.remove(it.key)
                kunNye.remove(nyVerdi)
            }
        }
        return DiffResultat(deletes=kunEksisterende.values, inserts=kunNye, updates=oppdaterMap)
    }

    data class DiffResultat(val deletes : Collection<Long>, val inserts : Collection<Verdi>, val updates : Map<Long, Verdi>)

    data class Verdi (val verdi: String, val oppgavefeltId: Long)
}