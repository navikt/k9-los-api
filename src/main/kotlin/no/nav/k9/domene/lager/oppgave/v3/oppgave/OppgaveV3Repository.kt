package no.nav.k9.domene.lager.oppgave.v3.oppgave

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
                    where id = :type and omrade_id = (select id 
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
                    oppgavefelter = tx.run(
                        queryOf(
                            """
                            select *
                            from oppgavefelt o
                            	inner join feltdefinisjon d on o.feltdefinisjon_id = d.id
                            where oppgavetype_id = :oppgavetypeId""",
                            mapOf("oppgavetypeId" to oppgavetypeRow.long("id"))
                        ).map { row ->
                            Oppgavefelt(
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
                )
            }.asSingle
        )
    }

    fun lagre(oppgave: OppgaveV3, tx: TransactionalSession) {
        // hente ut nyeste versjon(ekstern_id, område) i basen, sette aktuell versjon til inaktiv
        val eksisterendeVersjon = tx.run(
            queryOf(
                """
                select max(versjon) as versjon
                from oppgave_v3 o
                where o.ekstern_id = :eksternId
                and o.kildeomrade = :omrade
                and o.aktiv is true 
            """.trimIndent(),
                mapOf(
                    "eksternID" to oppgave.id,
                    "omrade" to oppgave.område
                )
            ).map { row -> row.long("versjon") }.asSingle
        )
        if (eksisterendeVersjon != null) {
            deaktiverVersjon(oppgave, eksisterendeVersjon, tx)
        }
        val nyVersjon = if (eksisterendeVersjon != null) eksisterendeVersjon+1 else 0

        // TODO: lagre oppgave med nyeste versjon +1
        // TODO: lagre oppgavefeltverdier
    }

    private fun deaktiverVersjon(oppgave: OppgaveV3, eksisterendeVersjon: Long, tx: TransactionalSession) {
        TODO()
    }
}