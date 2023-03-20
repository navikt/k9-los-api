package no.nav.k9.los.nyoppgavestyring.query.db

import org.postgresql.util.PGInterval
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDateTime

class SqlOppgaveQuery {

    private var query = """
                SELECT o.id as id
                FROM Oppgave_v3 o INNER JOIN Oppgavetype ot ON (
                    ot.id = o.oppgavetype_id
                  ) INNER JOIN Omrade oppgave_omrade ON (
                    oppgave_omrade.id = ot.omrade_id
                  )
                WHERE aktiv = true 
            """.trimIndent()

    private var orderBySql = """
                ORDER BY TRUE 
            """.trimIndent()

    private val queryParams: MutableMap<String, Any?> = mutableMapOf()
    private val orderByParams: MutableMap<String, Any?> = mutableMapOf()
    private var limit: Int = -1;

    fun getQuery(): String {
        return query + orderBySql
    }

    fun getParams(): Map<String, Any?> {
        return (queryParams + orderByParams).toMap()
    }

    fun medFeltverdi(combineOperator: CombineOperator, feltområde: String?, feltkode: String, operator: FeltverdiOperator, feltverdi: Any) {
        if (feltområde != null) {
            medOppgavefelt(combineOperator, feltområde, feltkode, operator, feltverdi)
            return
        }

        val index = queryParams.size;
        when (feltkode) {
            "oppgavestatus" -> {
                query += "${combineOperator.sql} o.status ${operator.sql} (:oppgavestatus$index) "
                queryParams["oppgavestatus$index"] = feltverdi
            }
            "kildeområde" -> {
                query += "${combineOperator.sql} o.kildeomrade ${operator.sql} (:kildeomrade$index) "
                queryParams["kildeomrade$index"] = feltverdi
            }
            "oppgavetype" -> {
                query += "${combineOperator.sql} o.ekstern_id ${operator.sql} (:oppgavetype$index) "
                queryParams["oppgavetype$index"] = feltverdi
            }
            "oppgaveområde" -> {
                query += "${combineOperator.sql} oppgave_omrade.ekstern_id ${operator.sql} (:oppgave_omrade$index) "
                queryParams["oppgave_omrade$index"] = feltverdi
            }
            else -> throw IllegalStateException("Ukjent feltkode: $feltkode")
        }
    }

    fun medBlokk(combineOperator: CombineOperator, defaultTrue: Boolean, blokk: () -> Unit) {
        query += "${combineOperator.sql} ("
        query += defaultTrue.toString()
        query += " "
        blokk()
        query += ") "
    }

    private fun medOppgavefelt(combineOperator: CombineOperator, feltområde: String, feltkode: String, operator: FeltverdiOperator, feltverdi: Any) {
        val index = queryParams.size

        /*
         * Postgres støtter ikke betinget typekonvertering av queryparametere. Dette fordi
         * typekonverteringen blir gjort ved opprettelse av spørring og at feilende
         * typekonvertering gjør at spørringen feiler.
         *
         * Mulig forbedring: Hvis vi cacher hvilke "tolkes_som" hvert enkelt felt er,
         * så kan vi legge til kun den datatypen som blir brukt.
         */
        val timestampFeltverdi = try {
            LocalDateTime.parse(feltverdi as String)
        } catch (e: Exception) { null }

        val durationFeltverdi = try {
            PGInterval(feltverdi as String)
        } catch (e: Exception) { null }

        val integerFeltverdi = try {
            BigInteger(feltverdi as String)
        } catch (e: Exception) { null }

        val doubleFeltverdi = try {
            BigDecimal(feltverdi as String)
        } catch (e: Exception) { null }

        queryParams.putAll(mutableMapOf(
            "feltOmrade$index" to feltområde,
            "feltkode$index" to feltkode,
            "feltverdi$index" to feltverdi,
            "timestamp_feltverdi$index" to timestampFeltverdi,
            "duration_feltverdi$index" to durationFeltverdi,
            "integer_feltverdi$index" to integerFeltverdi,
            "double_feltverdi$index" to doubleFeltverdi
        ))

        query += """
                ${combineOperator.sql} EXISTS (
                    SELECT 'Y'
                    FROM Oppgavefelt_verdi ov INNER JOIN Oppgavefelt f ON (
                      f.id = ov.oppgavefelt_id
                    ) INNER JOIN Feltdefinisjon fd ON (
                      fd.id = f.feltdefinisjon_id
                    ) INNER JOIN Omrade fo ON (
                      fo.id = fd.omrade_id
                    )
                    WHERE ov.oppgave_id = o.id
                      AND fo.ekstern_id = :feltOmrade$index
                      AND fd.ekstern_id = :feltkode$index
                      AND CASE
                          WHEN fd.tolkes_som = 'Timestamp' THEN CAST(ov.verdi AS timestamp) ${operator.sql} (:timestamp_feltverdi$index)
                          WHEN fd.tolkes_som = 'Duration' THEN CAST(ov.verdi AS interval) ${operator.sql} (:duration_feltverdi$index)
                          WHEN fd.tolkes_som = 'Integer' THEN CAST(ov.verdi AS integer) ${operator.sql} (:integer_feltverdi$index)
                          WHEN fd.tolkes_som = 'Double' THEN CAST(ov.verdi AS DOUBLE PRECISION) ${operator.sql} (:double_feltverdi$index)
                          ELSE ov.verdi ${operator.sql} (:feltverdi$index)
                        END
                  ) 
            """.trimIndent()
    }

    fun medEnkelOrder(feltområde: String?, feltkode: String, økende: Boolean) {
        if (feltområde != null) {
            medEnkelOrderAvOppgavefelt(feltområde, feltkode, økende)
            return
        }

        val index = queryParams.size;
        when (feltkode) {
            "oppgavestatus" -> {
                orderBySql += ", o.status "
            }
            "kildeområde" -> {
                orderBySql += ", o.kildeomrade "
            }
            "oppgavetype" -> {
                orderBySql += ", o.ekstern_id "
            }
            "oppgaveområde" -> {
                orderBySql += ", oppgave_omrade.ekstern_id "
            }
            else -> throw IllegalStateException("Ukjent feltkode: $feltkode")
        }

        orderBySql += if (økende) "ASC" else "DESC"
    }

    private fun medEnkelOrderAvOppgavefelt(feltområde: String, feltkode: String, økende: Boolean) {
        val index = orderByParams.size;

        orderByParams.putAll(mutableMapOf(
            "orderByfeltOmrade$index" to feltområde,
            "orderByfeltkode$index" to feltkode
        ))

        /*
         * Koden under håndterer sortering per datatype.
         *
         * Mulig forbedring: Hvis vi cacher hvilke "tolkes_som" hvert enkelt felt er,
         * så kan vi legge til kun den datatypen som blir brukt.
         */
        val conversions = arrayOf(
            "CASE WHEN fd.tolkes_som = 'Duration' THEN ov.verdi::interval ELSE NULL END",
            "CASE WHEN fd.tolkes_som = 'Integer' THEN ov.verdi::integer ELSE NULL END",
            "CASE WHEN fd.tolkes_som = 'Double' THEN CAST(ov.verdi AS DOUBLE PRECISION) ELSE NULL END",
            "CASE WHEN fd.tolkes_som = 'Timestamp' THEN ov.verdi::timestamp ELSE NULL END",
            "CASE WHEN fd.tolkes_som NOT IN ('Duration', 'Integer', 'Double', 'Timestamp') THEN ov.verdi ELSE NULL END"
        )

        for (typeConversion in conversions) {
            orderBySql += """
                    , (
                      SELECT $typeConversion                    
                      FROM Oppgavefelt_verdi ov INNER JOIN Oppgavefelt f ON (
                        f.id = ov.oppgavefelt_id
                      ) INNER JOIN Feltdefinisjon fd ON (
                        fd.id = f.feltdefinisjon_id
                      ) INNER JOIN Omrade fo ON (
                        fo.id = fd.omrade_id
                      )
                      WHERE ov.oppgave_id = o.id
                        AND fo.ekstern_id = :orderByfeltOmrade$index
                        AND fd.ekstern_id = :orderByfeltkode$index
                    ) 
                """.trimIndent()

            orderBySql += if (økende) "ASC" else "DESC"
        }
    }

    fun medLimit(limit: Int) {
        this.limit = limit;
    }
}