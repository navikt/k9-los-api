package no.nav.k9.nyoppgavestyring.oppgavetype

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.nyoppgavestyring.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.nyoppgavestyring.omraade.Område
import org.slf4j.LoggerFactory

class OppgavetypeRepository(private val feltdefinisjonRepository: FeltdefinisjonRepository) {

    private val log = LoggerFactory.getLogger(OppgavetypeRepository::class.java)

    fun hent(område: Område, tx: TransactionalSession): Oppgavetyper {
        val feltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
        val oppgavetypeListe = tx.run(
            queryOf(
                """
                select * from oppgavetype where omrade_id = :omradeId
            """.trimIndent(),
                mapOf(
                    "omradeId" to område.id
                )
            ).map { oppgavetypeRow ->
                Oppgavetype(
                    id = oppgavetypeRow.long("id"),
                    eksternId = oppgavetypeRow.string("ekstern_id"),
                    område = område,
                    oppgavefelter = tx.run(
                        queryOf(
                            """
                            select *
                            from oppgavefelt o
                            where oppgavetype_id = :oppgavetypeId""",
                            mapOf("oppgavetypeId" to oppgavetypeRow.long("id"))
                        ).map { row ->
                            Oppgavefelt(
                                id = row.long("id"),
                                feltDefinisjon = feltdefinisjoner.feltdefinisjoner.find { feltdefinisjon -> feltdefinisjon.id!!.equals(row.long("feltdefinisjon_id")) }
                                    ?: throw IllegalStateException("Oppgavetypens oppgavefelt referer til udefinert feltdefinisjon eller feltdefinisjon utenfor området")
                                ,
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
                         where oppgavetype_id = :oppgavetypeId 
                            and feltdefinisjon_id = :feltdefinisjonId""",
                        mapOf(
                            "oppgavetypeId" to oppgavetype.id,
                            "feltdefinisjonId" to oppgavefelt.feltDefinisjon.id
                        )
                    ).asUpdate
                )
            }
            tx.run(
                queryOf(
                    """
                    delete from oppgavetype
                    where id = :oppgavetypeId
                        and omrade_id = :omradeId""",
                    mapOf(
                        "oppgavetypeId" to oppgavetype.id,
                        "omradeId" to oppgavetyper.område.id
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
                        :omradeId,
                        :definisjonskilde)""",
                    mapOf(
                        "eksterntNavn" to oppgavetype.eksternId,
                        "omradeId" to oppgavetype.område.id,
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
                                :feltdefinisjonId,
                                :oppgavetypeId,
                                :paakrevd)""",
                        mapOf(
                            "feltdefinisjonId" to oppgavefelt.feltDefinisjon.id,
                            "oppgavetypeId" to oppgavetypeId,
                            "paakrevd" to oppgavefelt.påkrevd
                        ) //TODO: joine inn område_id? usikker på hva jeg synes om at feltdefinisjon.eksternt_navn alltid er prefikset med område
                    ).asUpdate
                )
            }
        }
    }
}