package no.nav.k9.los.nyoppgavestyring.query.db

import no.nav.k9.los.nyoppgavestyring.kodeverk.BeskyttelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.EgenAnsatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype.*
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.spi.felter.OrderByInput
import no.nav.k9.los.spi.felter.SqlMedParams
import no.nav.k9.los.spi.felter.TransientFeltutleder
import no.nav.k9.los.spi.felter.WhereInput
import java.time.LocalDateTime

class OppgaveQuerySqlBuilder(
    val felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
    val oppgavestatusFilter: List<Oppgavestatus>,
    val now: LocalDateTime,
    val fraAktiv: Boolean = true
) {
    private var selectPrefix = """
                SELECT o.id as id, o.kildeomrade as kildeomrade, o.ekstern_id as ekstern_id 
                """.trimIndent()

    private val oppgavefelterKodeOgType = felter.mapValues { Datatype.fraKode(it.value.oppgavefelt.tolkes_som) }

    private var query = (if (fraAktiv) """
        FROM oppgave_v3_aktiv o
        INNER JOIN oppgavetype ot ON ( ot.id = o.oppgavetype_id )
        INNER JOIN omrade oppgave_omrade ON (oppgave_omrade.id = ot.omrade_id )
        LEFT JOIN oppgave_pep_cache opc ON (o.kildeomrade = opc.kildeomrade AND o.ekstern_id = opc.ekstern_id)
        WHERE true 
    """ else """
        FROM oppgave_v3 o
        INNER JOIN oppgavetype ot ON ( ot.id = o.oppgavetype_id )
        INNER JOIN omrade oppgave_omrade ON (oppgave_omrade.id = ot.omrade_id )
        LEFT JOIN oppgave_pep_cache opc ON (o.kildeomrade = opc.kildeomrade AND o.ekstern_id = opc.ekstern_id)
        WHERE o.aktiv = true 
    """).trimIndent()

    private val filtrerReserverteOppgaver = """
        AND NOT EXISTS (
            select * 
            from reservasjon_v3 rv 
            where rv.reservasjonsnokkel = o.reservasjonsnokkel
            and upper(rv.gyldig_tidsrom) > :now 
            and rv.annullert_for_utlop = false 
        )
    """.trimIndent()

    private var orderBySql = """
                ORDER BY (select null) 
            """.trimIndent()

    private val queryParams: MutableMap<String, Any?> = mutableMapOf()
    private val orderByParams: MutableMap<String, Any?> = mutableMapOf()
    private var paging: String = ""

    fun getQuery(): String {
        return "$selectPrefix $query $orderBySql $paging"
    }

    fun medAntallSomResultat() {
        selectPrefix = """
            SELECT count(*) as antall 
        """.trimIndent()
    }

    fun utenReservasjoner() {
        query += filtrerReserverteOppgaver
        queryParams.put("now", now)
    }

    fun getParams(): Map<String, Any?> {
        return (queryParams + orderByParams).toMap()
    }

    private fun hentTransientFeltutleder(feltområde: String?, feltkode: String): TransientFeltutleder? {
        return felter[OmrådeOgKode(feltområde, feltkode)]?.transientFeltutleder
    }

    fun medFeltverdi(
        combineOperator: CombineOperator,
        feltområde: String?,
        feltkode: String,
        operator: FeltverdiOperator,
        feltverdi: Any?
    ) {
        hentTransientFeltutleder(feltområde, feltkode)?.let {
            val sqlMedParams = sikreUnikeParams(
                it.where(WhereInput(now, feltområde!!, feltkode, operator, feltverdi))
            )
            query += "${combineOperator.sql} " + sqlMedParams.query
            queryParams.putAll(sqlMedParams.queryParams)
            return
        }

        if (feltområde != null) {
            if (feltverdi == null) {
                utenOppgavefelt(combineOperator, feltområde, feltkode, operator)
            } else {
                medOppgavefelt(combineOperator, feltområde, feltkode, operator, feltverdi)
            }
            return
        }

        val index = queryParams.size + orderByParams.size
        when (feltkode) {
            "sistEndret" -> {
                query += "${combineOperator.sql} o.endret_tidspunkt ${operator.sql} (timestamp :sistEndret$index)) "
                queryParams["sistEndret$index"] = feltverdi
            }

            "oppgavestatus" -> {
                // det er forskjellig type på kolonnene i de forskjellige tabellene
                query += if (fraAktiv) {
                    "${combineOperator.sql} o.status ${operator.sql} (cast(:oppgavestatus$index as oppgavestatus)) "
                } else {
                    "${combineOperator.sql} o.status ${operator.sql} :oppgavestatus$index "
                }
                queryParams["oppgavestatus$index"] = feltverdi
            }

            "kildeområde" -> {
                query += "${combineOperator.sql} o.kildeomrade ${operator.sql} (:kildeomrade$index) "
                queryParams["kildeomrade$index"] = feltverdi
            }

            "oppgavetype" -> {
                query += "${combineOperator.sql} ot.ekstern_id ${operator.sql} (:oppgavetype$index) "
                queryParams["oppgavetype$index"] = feltverdi
            }

            "oppgaveområde" -> {
                query += "${combineOperator.sql} oppgave_omrade.ekstern_id ${operator.sql} (:oppgave_omrade$index) "
                queryParams["oppgave_omrade$index"] = feltverdi
            }

            //deprecated - for removal - bruk "personbeskyttelse" istedet
            "beskyttelse" -> {
                when (feltverdi) {
                    BeskyttelseType.KODE7.kode -> query += "${combineOperator.sql} opc.kode7 is not false "
                    else -> {
                        query += "${combineOperator.sql} opc.kode6 is not true AND opc.kode7 is not true "
                    }
                }
            }

            //deprecated - for removal - bruk "personbeskyttelse" istedet
            "egenAnsatt" -> {
                query += when (feltverdi) {
                    EgenAnsatt.JA.kode -> "${combineOperator.sql} opc.egen_ansatt is not false "
                    EgenAnsatt.NEI.kode -> "${combineOperator.sql} opc.egen_ansatt is not true "
                    else -> throw IllegalStateException("Ukjent feltkode: $feltkode")
                }
            }

            "personbeskyttelse" -> {
                query += when (feltverdi) {
                    //Dette er ikke tilgangskontroll, men tilordning av oppgaver til køer. Tilgangskontroll skjer når saksbehandlere plukker/ser på hva som er i køene.
                    //Negeringer i uttrykkene er for å slippe gjennom treff mot null (skjer om PEP_CACHE ikke er populert/er utdatert).
                    PersonBeskyttelseType.KODE6.kode -> "${combineOperator.sql} opc.kode6 is not false "
                    PersonBeskyttelseType.UTEN_KODE6.kode -> "${combineOperator.sql} opc.kode6 is not true "
                    PersonBeskyttelseType.KODE7_ELLER_EGEN_ANSATT.kode -> "${combineOperator.sql} (opc.kode6 is not true AND (opc.kode7 is not false OR opc.egen_ansatt is not false)) "
                    PersonBeskyttelseType.UGRADERT.kode -> "${combineOperator.sql} (opc.kode6 is not true AND opc.kode7 is not true AND opc.egen_ansatt is not true )"
                    else -> throw IllegalStateException("Ukjent feltkode: $feltkode")
                }
            }
        }
    }

    private fun sikreUnikeParams(sqlMedParams: SqlMedParams): SqlMedParams {
        var newQuery = sqlMedParams.query
        val newQueryParams = mutableMapOf<String, Any?>()
        sqlMedParams.queryParams.forEach { (oldKey, oldValue) ->
            val index = queryParams.size + orderByParams.size + newQueryParams.size
            val newKey = "$oldKey$index"
            newQuery = newQuery.replace(":$oldKey", ":$newKey")
            newQueryParams[newKey] = oldValue
        }
        return SqlMedParams(newQuery, newQueryParams)
    }

    fun medBlokk(combineOperator: CombineOperator, defaultTrue: Boolean, blokk: () -> Unit) {
        query += "${combineOperator.sql} ("
        query += defaultTrue.toString()
        query += " "
        blokk()
        query += ") "
    }

    private fun medOppgavefelt(
        combineOperator: CombineOperator,
        feltområde: String,
        feltkode: String,
        operator: FeltverdiOperator,
        feltverdi: Any
    ) {
        val index = queryParams.size + orderByParams.size

        val verdifelt = verdifelt(feltområde, feltkode)

        query += if (fraAktiv) {
            """
            ${combineOperator.sql} ${if (operator.negasjonAv != null) "NOT" else ""} EXISTS (
                SELECT 1
                FROM oppgavefelt_verdi_aktiv ov
                WHERE ov.oppgave_id = o.id
                  AND ov.omrade_ekstern_id = :feltOmrade$index
                  AND ov.feltdefinisjon_ekstern_id = :feltkode$index
                  AND $verdifelt ${operator.negasjonAv?.sql ?: operator.sql} (:feltverdi$index)
            ) 
            """.trimIndent()
        } else {
            """
            ${combineOperator.sql} ${if (operator.negasjonAv != null) "NOT" else ""} EXISTS (
                SELECT 1
                FROM oppgavefelt_verdi ov
                INNER JOIN oppgavefelt f ON (f.id = ov.oppgavefelt_id)
                INNER JOIN feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id)
                INNER JOIN omrade fo ON (fo.id = fd.omrade_id)
                WHERE ov.oppgave_id = o.id
                    AND fo.ekstern_id = :feltOmrade$index
                    AND fd.ekstern_id = :feltkode$index
                    AND $verdifelt ${operator.negasjonAv?.sql ?: operator.sql} (:feltverdi$index)
            ) 
            """.trimIndent()
        }

        queryParams.putAll(
            mapOf(
                "feltOmrade$index" to feltområde,
                "feltkode$index" to feltkode,
                "feltverdi$index" to feltverdi
            )
        )
    }

    private fun verdifelt(feltområde: String, feltkode: String): String {
        return when (oppgavefelterKodeOgType[OmrådeOgKode(feltområde, feltkode)]) {
            INTEGER -> "ov.verdi_bigint"
            else -> "ov.verdi"
        }
    }

    private fun utenOppgavefelt(combineOperator: CombineOperator, feltområde: String, feltkode: String, operator: FeltverdiOperator) {
        val index = queryParams.size + orderByParams.size

        queryParams.putAll(mutableMapOf(
            "feltOmrade$index" to feltområde,
            "feltkode$index" to feltkode
        ))

        val invertertOperator = when (operator) {
            FeltverdiOperator.EQUALS,
            FeltverdiOperator.IN -> " NOT"
            FeltverdiOperator.NOT_EQUALS,
            FeltverdiOperator.NOT_IN -> ""

            else -> throw IllegalArgumentException("Ugyldig operator for tom verdi.")
        }

        query += (if (fraAktiv) """
            ${combineOperator.sql}$invertertOperator EXISTS (
                SELECT 1
                FROM oppgavefelt_verdi_aktiv ov 
                WHERE ov.oppgave_id = o.id
                    AND ov.omrade_ekstern_id = :feltOmrade$index
                    AND ov.feltdefinisjon_ekstern_id = :feltkode$index
            )
        """ else """
            ${combineOperator.sql}$invertertOperator EXISTS (
                SELECT 1
                FROM oppgavefelt_verdi ov 
                INNER JOIN oppgavefelt f ON (f.id = ov.oppgavefelt_id) 
                INNER JOIN feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id) 
                INNER JOIN omrade fo ON (fo.id = fd.omrade_id)
                WHERE ov.oppgave_id = o.id
                    AND fo.ekstern_id = :feltOmrade$index
                    AND fd.ekstern_id = :feltkode$index
                    AND ov.omrade_ekstern_id = :feltOmrade$index
                    AND ov.feltdefinisjon_ekstern_id = :feltkode$index
            )
        """).trimIndent()
    }

    fun medEnkelOrder(feltområde: String?, feltkode: String, økende: Boolean) {
        if (feltområde != null) {
            medEnkelOrderAvOppgavefelt(feltområde, feltkode, økende)
            return
        }

        when (feltkode) {
            "oppgavestatus" -> {
                orderBySql += ", o.status "
            }
            "kildeområde" -> {
                orderBySql += ", o.kildeomrade "
            }
            "oppgavetype" -> {
                orderBySql += ", ot.ekstern_id "
            }
            "oppgaveområde" -> {
                orderBySql += ", oppgave_omrade.ekstern_id "
            }
            else -> throw IllegalStateException("Ukjent feltkode: $feltkode")
        }

        orderBySql += if (økende) "ASC" else "DESC"
    }

    private fun medEnkelOrderAvOppgavefelt(feltområde: String, feltkode: String, økende: Boolean) {
        hentTransientFeltutleder(feltområde, feltkode)?.let {
            val sqlMedParams = sikreUnikeParams(
                it.orderBy(OrderByInput(now, feltområde, feltkode, økende))
            )
            orderBySql += ", " + sqlMedParams.query
            orderByParams.putAll(sqlMedParams.queryParams)
            return
        }

        val index = queryParams.size + orderByParams.size

        orderByParams.putAll(mutableMapOf(
            "orderByfeltOmrade$index" to feltområde,
            "orderByfeltkode$index" to feltkode
        ))

        val verdifelt = verdifelt(feltområde, feltkode)
        orderBySql += if (fraAktiv) {
            """
                , (
                  SELECT $verdifelt                    
                  FROM oppgavefelt_verdi_aktiv ov 
                  WHERE ov.oppgave_id = o.id
                    AND ov.omrade_ekstern_id = :orderByfeltOmrade$index
                    AND ov.feltdefinisjon_ekstern_id = :orderByfeltkode$index
                ) 
            """.trimIndent()
        } else {
            """
                , (
                  SELECT $verdifelt                    
                  FROM oppgavefelt_verdi ov 
                  INNER JOIN oppgavefelt f ON (f.id = ov.oppgavefelt_id) 
                  INNER JOIN feltdefinisjon fd ON (fd.id = f.feltdefinisjon_id) 
                  INNER JOIN omrade fo ON (fo.id = fd.omrade_id)
                  WHERE ov.oppgave_id = o.id
                    AND fo.ekstern_id = :orderByfeltOmrade$index
                    AND fd.ekstern_id = :orderByfeltkode$index
                    AND ov.omrade_ekstern_id = :orderByfeltOmrade$index
                    AND ov.feltdefinisjon_ekstern_id = :orderByfeltkode$index
                ) 
            """.trimIndent()
        }

        orderBySql += if (økende) "ASC" else "DESC"
    }

    fun medPaging(limit: Long, offset: Long) {
        if (limit < 0) {
            return
        } else if (limit > 0 && offset < 0) {
            this.paging = "LIMIT $limit"
        } else if (limit > 0 && offset >= 0) {
            this.paging = "LIMIT $limit OFFSET $offset"
        }
    }

    /** Skal bare brukes til debugging, siden parametrene settes inn ukritisk */
    fun unsafeDebug(): String {
        var queryWithParams = getQuery()

        // erstatter placeholdere reversert siden f.eks. ':feltverdi1' også matcher ':feltverdi10'
        for ((key, value) in getParams().toSortedMap().reversed()) {
            queryWithParams = queryWithParams.replace(":$key", if (value is Number) value.toString() else "'" + value.toString() + "'")
        }

        return queryWithParams
    }

}
