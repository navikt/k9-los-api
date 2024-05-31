package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.OppgavetypeRepository
import javax.sql.DataSource

class OppgaveRepositoryKorreksjoner(
    private val dataSource: DataSource,
    private val oppgavetypeRepository: OppgavetypeRepository,
) {
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
                    "internVersjon" to internVersjon-1
                )
            ).map { row ->
                OppgaveV3(
                    id = row.long("id"),
                    eksternId = row.string("ekstern_id"),
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetype,
                    status = Oppgavestatus.valueOf(row.string("status")),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    reservasjonsnøkkel = row.stringOrNull("reservasjonsnokkel") ?: "mangler_historikkvask",
                    aktiv = row.boolean("aktiv"),
                    felter = hentFeltverdier(row.long("id"), oppgavetype, tx)
                )
            }.asSingle
        )
    }
}