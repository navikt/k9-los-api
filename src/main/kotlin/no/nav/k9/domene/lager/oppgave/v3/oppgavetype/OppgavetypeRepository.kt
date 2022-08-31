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
                mapOf(
                    "omradeId" to områdeId
                )
            ).map { oppgavetypeRow ->
                Oppgavetype(
                    id = oppgavetypeRow.string("ekstern_id"),
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
                                id = row.long("o.id"),
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
                    ).toSet(),
                    definisjonskilde = oppgavetypeRow.string("definisjonskilde")
                )
            }.asList
        )

        return Oppgavetyper(område, oppgavetypeListe.toSet())
    }

    fun fjern(oppgavetyper: Oppgavetyper, tx: TransactionalSession) {
        oppgavetyper.oppgavetyper.forEach { oppgavetype ->
            oppgavetype.oppgavefelter.forEach { oppgavefelt ->
                tx.run(
                    queryOf(
                        """
                         delete from oppgavefelt
                         where oppgavetype_id = (select id from oppgavetype where ekstern_id = :oppgavetype) 
                            and feltdefinisjon_id = (
                                select id
                                from feltdefinisjon
                                where eksternt_navn = :feltnavn
                                    and omrade_id = (select id from omrade where ekstern_id = :omrade)
                            )""",
                        mapOf(
                            "oppgavetype" to oppgavetype.id,
                            "feltnavn" to oppgavefelt.feltDefinisjon.navn,
                            "omrade" to oppgavetyper.område
                        )
                    ).asUpdate
                )
            }
            tx.run(
                queryOf(
                    """
                    delete from oppgavetype
                    where ekstern_id = :ekstern_id
                        and omrade_id = (select id from omrade where ekstern_id = :omrade)""",
                    mapOf(
                        "ekstern_id" to oppgavetype.id,
                        "omrade" to oppgavetyper.område
                    )
                ).asUpdate
            )
        }
    }

    fun leggTil(oppgavetyper: Oppgavetyper, tx: TransactionalSession) {
        oppgavetyper.oppgavetyper.forEach { oppgavetype ->
            val oppgavetypeId = tx.run(
                queryOf(
                    """
                    insert into oppgavetype(ekstern_id, omrade_id, definisjonskilde)
                    values(
                        :eksterntNavn,
                        (select id from omrade where ekstern_id = :omradeId),
                        :definisjonskilde)""",
                    mapOf(
                        "eksterntNavn" to oppgavetype.id,
                        "omradeId" to oppgavetyper.område,
                        "definisjonskilde" to oppgavetype.definisjonskilde
                    )
                ).asUpdateAndReturnGeneratedKey
            )!!
            oppgavetype.oppgavefelter.forEach { oppgavefelt ->
                tx.run(
                    queryOf(
                        """
                            insert into oppgavefelt(feltdefinisjon_id, oppgavetype_id, pakrevd)
                            values(
                                (select id from feltdefinisjon where eksternt_navn = :feltnavn),
                                :oppgavetypeId,
                                :paakrevd)""",
                        mapOf(
                            "feltnavn" to oppgavefelt.feltDefinisjon.navn,
                            "oppgavetypeId" to oppgavetypeId,
                            "paakrevd" to oppgavefelt.påkrevd
                        ) //TODO: joine inn område_id? usikker på hva jeg synes om at feltdefinisjon.eksternt_navn alltid er prefikset med område
                    ).asUpdate
                )
            }
        }
    }
}