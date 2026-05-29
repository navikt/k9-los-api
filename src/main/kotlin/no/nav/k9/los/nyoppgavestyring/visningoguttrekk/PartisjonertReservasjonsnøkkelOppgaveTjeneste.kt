package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import java.time.LocalDateTime

class PartisjonertReservasjonsnøkkelOppgaveTjeneste(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val transactionalManager: TransactionalManager,
) : ReservasjonsnøkkelOppgaveTjeneste {

    override fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave> {
        return transactionalManager.transaction { tx ->
            hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel, tx)
        }
    }

    override fun hentÅpneOppgaverForReservasjonsnøkkel(
        reservasjonsnøkkel: String,
        tx: TransactionalSession
    ): List<Oppgave> {
        val now = LocalDateTime.now()

        val rader = tx.run(
            queryOf(
                """
                    SELECT *
                    FROM oppgave_v3_part
                    WHERE reservasjonsnokkel = :reservasjonsnokkel
                      AND oppgavestatus IN ('AAPEN', 'VENTER', 'UAVKLART')
                    """.trimIndent(),
                mapOf("reservasjonsnokkel" to reservasjonsnøkkel)
            ).map { it.tilOppgaveRad() }.asList
        )
        return rader.map { rad ->
            val oppgavetypeObj = oppgavetypeRepository.hentOppgavetype("K9", rad.oppgavetypeEksternId, tx)
            val oppgavefelter = hentOppgavefelter(tx, rad.id, oppgavetypeObj)
            Oppgave(
                eksternId = rad.oppgaveEksternId,
                eksternVersjon = rad.oppgaveEksternVersjon,
                oppgavetype = oppgavetypeObj,
                status = rad.oppgavestatus,
                endretTidspunkt = rad.endretTidspunkt,
                felter = oppgavefelter,
                reservasjonsnøkkel = rad.reservasjonsnokkel,
            ).fyllDefaultverdier().utledTransienteFelter(now)
        }
    }

    private fun hentOppgavefelter(
        tx: TransactionalSession,
        oppgaveId: Long,
        oppgavetype: Oppgavetype
    ): List<Oppgavefelt> {
        return tx.run(
            queryOf(
                """
                select ov.feltdefinisjon_ekstern_id as ekstern_id, fd.liste_type, f.pakrevd, ov.verdi, ov.verdi_bigint
                from oppgavefelt_verdi_part ov
                inner join feltdefinisjon fd on ov.feltdefinisjon_ekstern_id = fd.ekstern_id
                inner join oppgavefelt f on fd.id = f.feltdefinisjon_id and f.oppgavetype_id = :oppgavetypeId
                where ov.oppgave_id = :oppgaveId
                order by ov.feltdefinisjon_ekstern_id
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId, "oppgavetypeId" to oppgavetype.id)
            ).map { row ->
                Oppgavefelt(
                    eksternId = row.string("ekstern_id"),
                    område = "K9",
                    listetype = row.boolean("liste_type"),
                    påkrevd = row.boolean("pakrevd"),
                    verdi = row.string("verdi"),
                    verdiBigInt = row.longOrNull("verdi_bigint"),
                )
            }.asList
        )
    }
}
