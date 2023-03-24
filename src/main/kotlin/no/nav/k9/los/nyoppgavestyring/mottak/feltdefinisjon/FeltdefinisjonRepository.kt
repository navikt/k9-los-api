package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.feilhandtering.IllegalDeleteException
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory

class FeltdefinisjonRepository {
    private val log = LoggerFactory.getLogger(FeltdefinisjonRepository::class.java)

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
                    visTilBruker = row.boolean("vis_til_bruker"),
                )
            }.asList
        )
        return Feltdefinisjoner(område, feltdefinisjoner.toSet())
    }

    fun fjern(sletteListe: Set<Feltdefinisjon>, tx: TransactionalSession) {
        sletteListe.forEach { datatype ->
            try {
                tx.run(
                    queryOf(
                        """
                        delete from feltdefinisjon where id = :id""",
                        mapOf(
                            "id" to datatype.id,
                        )
                    ).asUpdate
                )
            } catch (e: PSQLException) {
                if (e.sqlState.equals("23503")) {
                    throw IllegalDeleteException("Kan ikke slette feltdefinisjon som brukes av oppgavetype", e)
                } else {
                    throw e
                }
            }
        }
    }

    fun oppdater(oppdaterlListe: Set<Feltdefinisjon>, område: Område, tx: TransactionalSession) {
        oppdaterlListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                    update feltdefinisjon 
                    set liste_type = :listeType, tolkes_som = :tolkesSom, vis_til_bruker = :visTilBruker
                    WHERE omrade_id = :omradeId AND ekstern_id = :eksternId""",
                    mapOf(
                        "eksternId" to datatype.eksternId,
                        "omradeId" to område.id,
                        "listeType" to datatype.listetype,
                        "tolkesSom" to datatype.tolkesSom,
                        "visTilBruker" to datatype.visTilBruker
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
                    insert into feltdefinisjon(ekstern_id, omrade_id, liste_type, tolkes_som, vis_til_bruker) 
                    values(:eksternId, :omradeId, :listeType, :tolkesSom, :visTilBruker)""",
                    mapOf(
                        "eksternId" to datatype.eksternId,
                        "omradeId" to område.id,
                        "listeType" to datatype.listetype,
                        "tolkesSom" to datatype.tolkesSom,
                        "visTilBruker" to datatype.visTilBruker
                    )
                ).asUpdate
            )
        }
    }
}