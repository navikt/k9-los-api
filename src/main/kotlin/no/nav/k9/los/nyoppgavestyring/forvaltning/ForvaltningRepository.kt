package no.nav.k9.los.nyoppgavestyring.forvaltning

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import java.time.LocalDateTime

class ForvaltningRepository(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val transactionalManager: TransactionalManager,
) {

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

}