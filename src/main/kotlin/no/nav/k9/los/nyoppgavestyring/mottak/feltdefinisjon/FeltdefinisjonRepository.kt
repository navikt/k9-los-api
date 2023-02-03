package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import org.slf4j.LoggerFactory

class FeltdefinisjonRepository {

    private val log = LoggerFactory.getLogger(FeltdefinisjonRepository::class.java)


    fun lås(tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                    lock table feltdefinisjon
                """.trimIndent()
            ).asExecute
        )
    }

    fun hent(område: Område, tx: TransactionalSession): Feltdefinisjoner {
        val feltdefinisjoner = tx.run(
            queryOf(
                """
                select * from feltdefinisjon 
                where omrade_id = :omradeId
                for update
            """.trimIndent(),
                mapOf("omradeId" to område.id)
            ).map { row ->
                Feltdefinisjon(
                    id = row.long("id"),
                    eksternId = row.string("ekstern_id"),
                    område = område,
                    listetype = row.boolean("liste_type"),
                    tolkesSom = row.string("tolkes_som"),
                    visTilBruker = true
                )
            }.asList
        )
        return Feltdefinisjoner(område, feltdefinisjoner.toSet())
    }

    fun fjern(sletteListe: Set<Feltdefinisjon>, tx: TransactionalSession) {
        sletteListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                        delete from feltdefinisjon where id = :id""",
                    mapOf(
                        "id" to datatype.id,
                    )
                ).asUpdate
            )
        }
    }

    fun leggTil(leggTilListe: Set<Feltdefinisjon>, område: Område, tx: TransactionalSession) {
        leggTilListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                    insert into feltdefinisjon(ekstern_id, omrade_id, liste_type, tolkes_som) 
                    values(:eksternId, :omradeId, :listeType, :tolkesSom)""",
                    mapOf(
                        "eksternId" to datatype.eksternId,
                        "omradeId" to område.id,
                        "listeType" to datatype.listetype,
                        "tolkesSom" to datatype.tolkesSom
                    )
                ).asUpdate
            )
        }
    }
}