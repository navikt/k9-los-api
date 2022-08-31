package no.nav.k9.domene.lager.oppgave.v3.oppgave

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjon
import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavefelt
import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavetype
import org.slf4j.LoggerFactory

class OppgaveV3Repository {

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

    fun hentOppgaveType(område: String, type: String, tx: TransactionalSession): Oppgavetype? {
        return tx.run(
            queryOf(
                """select * 
                    from oppgavetype 
                    where ekstern_id = :type and omrade_id = (select id 
                    from omrade 
                    where ekstern_id = :omrade)""".trimIndent(),
                mapOf(
                    "type" to type,
                    "omrade" to område
                )
            ).map { oppgavetypeRow ->
                Oppgavetype(
                    id = oppgavetypeRow.string("ekstern_id"),
                    definisjonskilde = oppgavetypeRow.string("definisjonskilde"),
                    oppgavefelter = hentOppgavefelter(tx, oppgavetypeRow)
                )
            }.asSingle
        )
    }

    private fun hentOppgavefelter(
        tx: TransactionalSession,
        oppgavetypeRow: Row
    ) = tx.run(
        queryOf(
            """
                select * 
                from oppgavefelt o
                inner join feltdefinisjon d on o.feltdefinisjon_id = d.id
                where oppgavetype_id = :oppgavetypeId""",
            mapOf("oppgavetypeId" to oppgavetypeRow.long("id"))
        ).map { row ->
            Oppgavefelt(
                id = row.long("id"),
                feltDefinisjon = Feltdefinisjon(
                    navn = row.string("eksternt_navn"),
                    listetype = row.boolean("liste_type"),
                    parsesSom = row.string("parses_som"),
                    visTilBruker = true
                ),
                påkrevd = row.boolean("pakrevd"),
                visPåOppgave = true
            )
        }.asList
    ).toSet()

    fun lagre(oppgave: OppgaveV3, tx: TransactionalSession) {
        // hente ut nyeste versjon(ekstern_id, område) i basen, sette aktuell versjon til inaktiv
        val eksisterendeVersjon = hentVersjon(tx, oppgave)

        eksisterendeVersjon?.let { deaktiverVersjon(oppgave, it, tx) }

        val nyVersjon = eksisterendeVersjon?.plus(1) ?: 0

        val oppgaveId = lagre(oppgave, nyVersjon, tx)
        // TODO: lagre oppgavefeltverdier
    }

    private fun hentVersjon(tx: TransactionalSession, oppgave: OppgaveV3): Long? {
        return tx.run(
            queryOf(
                """
                    select max(versjon) as versjon
                    from oppgave_v3 o
                    where o.ekstern_id = :eksternId
                    and o.kildeomrade = :omrade
                    and o.aktiv is true 
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.id,
                    "omrade" to oppgave.område
                )
            ).map { row -> row.longOrNull("versjon") }.asSingle
        )
    }

    private fun lagre(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession): Long  {
        return tx.updateAndReturnGeneratedKey(
            queryOf("""
                    insert into oppgave_v3(ekstern_id, oppgavetype_id, status, versjon, aktiv, kildeomrade, endret_tidspunkt)
                    values(:eksternId, :oppgavetypeId, :status, :versjon, :aktiv, :kildeomrade, CURRENT_TIMESTAMP)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.id,
                    "oppgavetypeId" to oppgave.type, // TODO heller ha id'n til oppgavetype på oppgaveobjektet
                    "status" to oppgave.status,
                    "aktiv" to true,
                    "kildeomrade" to oppgave.område, // TODO heller ha id til område her
                )
            )
        )!!
    }

    private fun deaktiverVersjon(oppgave: OppgaveV3, eksisterendeVersjon: Long, tx: TransactionalSession) {
        TODO()
    }
}