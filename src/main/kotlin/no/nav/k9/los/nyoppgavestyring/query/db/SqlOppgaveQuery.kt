package no.nav.k9.los.nyoppgavestyring.query.db

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

    private val queryParams: MutableMap<String, Any?> = mutableMapOf()

    fun getQuery(): String {
        System.out.println(query)
        return query;
    }

    fun getParams(): Map<String, Any?> {
        return queryParams.toMap()
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
                queryParams[":oppgavestatus$index"] = feltverdi
            }
            "kildeområde" -> {
                query += "${combineOperator.sql} o.kildeomrade ${operator.sql} (:kildeomrade$index) "
                queryParams[":kildeomrade$index"] = feltverdi
            }
            "oppgavetype" -> {
                query += "${combineOperator.sql} o.ekstern_id ${operator.sql} (:oppgavetype$index) "
                queryParams[":oppgavetype$index"] = feltverdi
            }
            "oppgaveområde" -> {
                query += "${combineOperator.sql} oppgave_omrade.ekstern_id ${operator.sql} (:oppgave_omrade$index) "
                queryParams[":oppgave_omrade$index"] = feltverdi
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
        val index = queryParams.size;

        queryParams.putAll(mutableMapOf(
            "feltOmrade$index" to feltområde,
            "feltkode$index" to feltkode,
            "feltverdi$index" to feltverdi
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
                      AND ov.verdi ${operator.sql} (:oppgaveVerdi$index)
                  ) 
            """.trimIndent()
    }
}