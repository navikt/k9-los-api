package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import org.slf4j.LoggerFactory

class FeltdefinisjonRepository(private val områdeRepository: OmrådeRepository) {

    private val log = LoggerFactory.getLogger(FeltdefinisjonRepository::class.java)

    fun hent(område: String, tx: TransactionalSession): Feltdefinisjoner {
        val feltdefinisjoner = tx.run(
            queryOf(
                """
                select * from feltdefinisjon 
                where omrade_id = (select id from omrade where omrade.ekstern_id = :omrade)
                for update
            """.trimIndent(),
                mapOf("omrade" to område)
            ).map { row ->
                Feltdefinisjon(
                    navn = row.string("eksternt_navn"),
                    listetype = row.boolean("liste_type"),
                    parsesSom = row.string("parses_som"),
                    visTilBruker = true
                )
            }.asList
        )
        return Feltdefinisjoner(område, feltdefinisjoner.toSet())
    }

    fun fjern(område: String, sletteListe: Set<Feltdefinisjon>, tx: TransactionalSession) {
        sletteListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                        delete from feltdefinisjon where eksternt_navn = :eksterntNavn and omrade = :omrade""",
                    mapOf(
                        "eksterntNavn" to datatype.navn,
                        "omrade" to område
                    )
                ).asUpdate
            )
        }
    }

    fun leggTil(leggTilListe: Set<Feltdefinisjon>, område: String, tx: TransactionalSession) {
        val områdeId = områdeRepository.hentOmrådeId(område, tx)
        leggTilListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                    insert into feltdefinisjon(eksternt_navn, omrade_id, liste_type, parses_som) 
                    values(:eksterntNavn, :omradeId, :listeType, :parsesSom)""",
                    mapOf(
                        "eksterntNavn" to datatype.navn,
                        "omradeId" to områdeId,
                        "listeType" to datatype.listetype,
                        "parsesSom" to datatype.parsesSom
                    )
                ).asUpdate
            )
        }
    }
}