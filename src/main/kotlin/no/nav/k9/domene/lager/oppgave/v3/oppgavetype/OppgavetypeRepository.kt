package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjon
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import org.slf4j.LoggerFactory

class OppgavetypeRepository(private val områdeRepository: OmrådeRepository) {

    private val log = LoggerFactory.getLogger(OppgavetypeRepository::class.java)

    fun hent(område: String, tx: TransactionalSession): Oppgavetyper {
        val områdeId = områdeRepository.hentOmrådeId(område, tx)
        val oppgavetypeListe = tx.run(
            queryOf(
                """
                select * from oppgavetype where omrade_id = :omradeId
            """.trimIndent(),
                mapOf("omradeId" to områdeId)
            ).map { oppgavetypeRow ->
                Oppgavetype(
                    id = oppgavetypeRow.string("ekstern_id"),
                    oppgavefelter = tx.run(
                        queryOf(
                            """
                            select *
                            from oppgavefelt o
                            	inner join feltdefinisjon d on o.feltdefinisjon_id = d.id
                            where oppgavetype_id = :oppgavetypeId
                        """.trimIndent(),
                            mapOf("oppgavetypeId" to oppgavetypeRow.long("id"))
                        ).map { row ->
                            Oppgavefelt(
                                feltDefinisjon = Feltdefinisjon(
                                    eksterntNavn = row.string("eksternt_navn"),
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
            }.asList
        )

        return Oppgavetyper(område, "", oppgavetypeListe.toSet())
    }
}