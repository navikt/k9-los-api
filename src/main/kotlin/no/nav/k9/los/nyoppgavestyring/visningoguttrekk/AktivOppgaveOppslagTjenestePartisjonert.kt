package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import java.time.LocalDateTime

class AktivOppgaveOppslagTjenestePartisjonert(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val transactionalManager: TransactionalManager,
) : AktivOppgaveOppslagTjeneste {

    override fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String): Oppgave {
        return transactionalManager.transaction { tx -> hentAktivOppgave(eksternId, oppgavetypeEksternId, tx) }
    }

    override fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String, tx: TransactionalSession): Oppgave {
        return hentAktivOppgaveHvisFinnes(eksternId, oppgavetypeEksternId, tx)
            ?: throw IllegalStateException("Fant ikke aktiv oppgave med eksternId=$eksternId og oppgavetype=$oppgavetypeEksternId")
    }

    override fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetypeEksternId: String): Oppgave? {
        return transactionalManager.transaction { tx ->
            hentAktivOppgaveHvisFinnes(eksternId, oppgavetypeEksternId, tx)
        }
    }

    override fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetypeEksternId: String, tx: TransactionalSession): Oppgave? {
        val now = LocalDateTime.now()

        data class OppgaveRad(
            val id: Long,
            val oppgavetypeEksternId: String,
            val oppgaveEksternId: String,
            val oppgaveEksternVersjon: String,
            val oppgavestatus: String,
            val endretTidspunkt: LocalDateTime,
            val reservasjonsnokkel: String,
        )

        val rad = tx.run(
            queryOf(
                """
                    SELECT o.*
                    FROM oppgave_id_part ip
                    INNER JOIN oppgave_v3_part o ON o.id = ip.id
                    WHERE ip.oppgave_ekstern_id = :eksternId
                      AND ip.oppgavetype_ekstern_id = :oppgavetype
                    """.trimIndent(),
                mapOf("eksternId" to eksternId, "oppgavetype" to oppgavetypeEksternId)
            ).map { row ->
                OppgaveRad(
                    id = row.long("id"),
                    oppgavetypeEksternId = row.string("oppgavetype_ekstern_id"),
                    oppgaveEksternId = row.string("oppgave_ekstern_id"),
                    oppgaveEksternVersjon = row.string("oppgave_ekstern_versjon"),
                    oppgavestatus = row.string("oppgavestatus"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    reservasjonsnokkel = row.string("reservasjonsnokkel"),
                )
            }.asSingle
        ) ?: return null

        val oppgavetypeObj = oppgavetypeRepository.hentOppgavetype("K9", rad.oppgavetypeEksternId, tx)
        val oppgavefelter = hentOppgavefelter(tx, rad.id, oppgavetypeObj)
        return Oppgave(
            eksternId = rad.oppgaveEksternId,
            eksternVersjon = rad.oppgaveEksternVersjon,
            oppgavetype = oppgavetypeObj,
            status = rad.oppgavestatus,
            endretTidspunkt = rad.endretTidspunkt,
            felter = oppgavefelter,
            reservasjonsnøkkel = rad.reservasjonsnokkel,
        ).fyllDefaultverdier().utledTransienteFelter(now)
    }

    private fun hentOppgavefelter(tx: TransactionalSession, oppgaveId: Long, oppgavetype: Oppgavetype): List<Oppgavefelt> {
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
