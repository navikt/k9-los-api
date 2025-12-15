package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.Row
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.nyoppgavestyring.kodeverk.BeskyttelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.EgenAnsatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.PartisjonertOppgaveId
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.mapping.*
import no.nav.k9.los.nyoppgavestyring.spi.felter.OrderByInput
import no.nav.k9.los.nyoppgavestyring.spi.felter.SqlMedParams
import no.nav.k9.los.nyoppgavestyring.spi.felter.TransientFeltutleder
import no.nav.k9.los.nyoppgavestyring.spi.felter.WhereInput
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class PartisjonertOppgaveQuerySqlBuilder(
    val felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
    oppgavestatusFilter: List<Oppgavestatus>,
    val now: LocalDateTime,
    ferdigstiltDatoFilter: FeltverdiOppgavefilter?,
) : OppgaveQuerySqlBuilder {
    private val log = LoggerFactory.getLogger(PartisjonertOppgaveQuerySqlBuilder::class.java)

    private val oppgavefelterKodeOgType = felter.mapValues { Datatype.fraKode(it.value.oppgavefelt.tolkes_som) }
    private val queryParams: MutableMap<String, Any?> = mutableMapOf()
    private val orderByParams: MutableMap<String, Any?> = mutableMapOf()
    private val ferdigstiltDatoParams = mutableMapOf<String, Any?>()

    private val oppgavestatusPlaceholder: String = InClauseHjelper.tilParameternavn(oppgavestatusFilter, "status")
    private val oppgavestatusParams =
        InClauseHjelper.parameternavnTilVerdierMap(oppgavestatusFilter.map { it.kode }, "status")

    private var ferdigstiltDatoBetingelse: (tabellalias: String) -> String = { "" }

    init {
        if (ferdigstiltDatoFilter != null) {
            initialiserFerdigstiltDatoFilter(ferdigstiltDatoFilter)
        }
    }

    private fun Any?.tilLocalDate(): LocalDate? {
        return when (this) {
            is LocalDate -> this
            is String -> LocalDate.parse(this)
            else -> null
        }
    }

    private fun initialiserFerdigstiltDatoFilter(ferdigstiltDatoFilter: FeltverdiOppgavefilter) {
        when (ferdigstiltDatoFilter.operator) {
            EksternFeltverdiOperator.LESS_THAN_OR_EQUALS, EksternFeltverdiOperator.LESS_THAN, EksternFeltverdiOperator.GREATER_THAN, EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, EksternFeltverdiOperator.NOT_EQUALS, EksternFeltverdiOperator.EQUALS -> {
                val operator = ferdigstiltDatoFilter.operator.tilFeltverdiOperator().sql
                ferdigstiltDatoBetingelse = { "AND $it.ferdigstilt_dato $operator :ferdigstilt_dato" }
                ferdigstiltDatoParams["ferdigstilt_dato"] = ferdigstiltDatoFilter.verdi.first().tilLocalDate()
            }

            EksternFeltverdiOperator.IN, EksternFeltverdiOperator.NOT_IN -> {
                val operator = ferdigstiltDatoFilter.operator.tilFeltverdiOperator().sql
                ferdigstiltDatoBetingelse = {
                    "AND $it.ferdigstilt_dato $operator (${
                        InClauseHjelper.tilParameternavn(
                            ferdigstiltDatoFilter.verdi,
                            "ferdigstilt_dato"
                        )
                    })"
                }
                ferdigstiltDatoParams.putAll(
                    InClauseHjelper.parameternavnTilVerdierMap(
                        ferdigstiltDatoFilter.verdi.map { it.tilLocalDate() },
                        "ferdigstilt_dato"
                    )
                )
            }

            EksternFeltverdiOperator.INTERVAL -> {
                ferdigstiltDatoBetingelse =
                    { "AND $it.ferdigstilt_dato BETWEEN :ferdigstilt_dato_fra AND :ferdigstilt_dato_til" }
                ferdigstiltDatoParams["ferdigstilt_dato_fra"] = ferdigstiltDatoFilter.verdi[0].tilLocalDate()
                ferdigstiltDatoParams["ferdigstilt_dato_til"] = ferdigstiltDatoFilter.verdi[1].tilLocalDate()
            }
        }
    }

    private var selectClause = "SELECT o.id, o.oppgave_ekstern_id, o.oppgave_ekstern_versjon"
    private var fromClause = """
        FROM oppgave_v3_part o
        LEFT JOIN oppgave_pep_cache opc ON (opc.kildeomrade = 'K9' AND o.oppgave_ekstern_id = opc.ekstern_id)
    """.trimIndent()
    
    private var whereClause = "WHERE o.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("o")}"
    private val orderByClauses = mutableListOf<String>()
    private val orderByClause get() = if (orderByClauses.isNotEmpty()) "ORDER BY " + orderByClauses.joinToString(", ") else ""
    private var pagingClause = ""

    private val utenReservasjonerBetingelse = """
        AND NOT EXISTS (
            SELECT 1 
            FROM reservasjon_v3 rv 
            WHERE rv.reservasjonsnokkel = o.reservasjonsnokkel
            AND upper(rv.gyldig_tidsrom) > :now 
            AND rv.annullert_for_utlop = false 
        )
    """.trimIndent()

    override fun filterRens(
        felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
        filtere: List<Oppgavefilter>
    ): List<Oppgavefilter> {
        return filtere
            .let { FilterFjerner.fjern(it, "oppgavestatus") }
            .let { FilterFjerner.fjern(it, "spørringstrategi") }
            .let { FilterFjerner.fjern(it, "ferdigstiltDato") }
            .let { OppgavefilterUtenBetingelserFjerner.fjern(it) }
            .let { OppgavefilterOperatorKorrigerer.korriger(it) }
            .let { OppgavefilterNullUtvider.utvid(it) }
            .let { OppgavefilterLocalDateSpesialhåndterer.spesialhåndter(it) }
            .let { OppgavefilterDatatypeMapper.map(felter, it) }
    }

    override fun getQuery(): String {
        return "$selectClause $fromClause $whereClause $orderByClause $pagingClause"
    }

    override fun medAntallSomResultat() {
        selectClause = "SELECT COUNT(*) as antall"
        orderByClauses.clear()
        orderByParams.clear()
    }

    override fun utenReservasjoner() {
        whereClause += " $utenReservasjonerBetingelse"
        queryParams["now"] = now
    }

    override fun medPaging(limit: Long, offset: Long) {
        val limitClause = if (limit > 0) "LIMIT $limit" else ""
        val offsetClause = if (offset > 0) "OFFSET $offset" else ""
        pagingClause = listOf(limitClause, offsetClause).joinToString(" ")
    }

    override fun medFeltverdi(
        combineOperator: CombineOperator,
        feltområde: String?,
        feltkode: String,
        operator: FeltverdiOperator,
        feltverdier: List<Any?>
    ) {
        // Håndterer de tre forskjellige felttypene

        // 1. Transient felt
        hentTransientFeltutleder(feltområde, feltkode)?.let {
            if (feltverdier.size > 1) {
                // Ikke støtte for flerverdier, så håndterer som flere enkeltverdier
                feltverdier.forEach { feltverdi ->
                    medFeltverdi(combineOperator, feltområde, feltkode, operator, listOf(feltverdi))
                }
                return@medFeltverdi
            }

            val sqlMedParams = sikreUnikeParams(
                it.where(
                    WhereInput(
                        Spørringstrategi.PARTISJONERT,
                        now,
                        feltområde!!,
                        feltkode,
                        operator,
                        feltverdier.first()
                    )
                )
            )
            whereClause += " ${combineOperator.sql} " + sqlMedParams.query
            queryParams.putAll(sqlMedParams.queryParams)
            return@medFeltverdi
        }

        // 2. Felt med område
        if (feltområde != null) {
            // Hvis null-verdi er det kun en verdi, allerede håndtert i filterRens
            if (feltverdier.first() == null) {
                utenOppgavefelt(combineOperator, feltkode, operator)
            } else {
                medOppgavefelt(combineOperator, feltområde, feltkode, operator, feltverdier)
            }
            return
        }

        // 3. Spesielle felt uten område
        val index = queryParams.size + orderByParams.size
        when (feltkode) {
            "oppgavetype" -> {
                val (feltverdiPlaceholder, feltVerdiMap) = if (feltverdier.size > 1) {
                    val prefix = "oppgavetype${index * 1000}"
                    "(${
                        InClauseHjelper.tilParameternavn(
                            feltverdier,
                            prefix
                        )
                    })" to InClauseHjelper.parameternavnTilVerdierMap(feltverdier, prefix)
                } else {
                    ":oppgavetype$index" to mapOf("oppgavetype$index" to feltverdier.first())
                }

                whereClause += " ${combineOperator.sql} o.oppgavetype_ekstern_id ${operator.sql} $feltverdiPlaceholder"
                queryParams.putAll(feltVerdiMap)
            }

            "personbeskyttelse" -> {
                whereClause += " ${combineOperator.sql} " + when (feltverdier.first()) {
                    PersonBeskyttelseType.KODE6.kode -> "opc.kode6 IS NOT FALSE"
                    PersonBeskyttelseType.UTEN_KODE6.kode -> "opc.kode6 IS NOT TRUE"
                    PersonBeskyttelseType.KODE7_ELLER_EGEN_ANSATT.kode -> "(opc.kode6 IS NOT TRUE AND (opc.kode7 IS NOT FALSE OR opc.egen_ansatt IS NOT FALSE))"
                    PersonBeskyttelseType.UGRADERT.kode -> "(opc.kode6 IS NOT TRUE AND opc.kode7 IS NOT TRUE AND opc.egen_ansatt IS NOT TRUE)"
                    else -> throw IllegalStateException("Ukjent verdi for personbeskyttelse: ${feltverdier.first()}")
                }
            }

            // Deprecated felter - vil bli fjernet
            "beskyttelse" -> {
                whereClause += " ${combineOperator.sql} " + when (feltverdier.first()) {
                    BeskyttelseType.KODE7.kode -> "opc.kode7 IS NOT FALSE"
                    else -> "opc.kode6 IS NOT TRUE AND opc.kode7 IS NOT TRUE"
                }
            }

            "egenAnsatt" -> {
                whereClause += " ${combineOperator.sql} " + when (feltverdier.first()) {
                    EgenAnsatt.JA.kode -> "opc.egen_ansatt IS NOT FALSE"
                    EgenAnsatt.NEI.kode -> "opc.egen_ansatt IS NOT TRUE"
                    else -> throw IllegalStateException("Ukjent verdi for egenAnsatt: ${feltverdier.first()}")
                }
            }

            else -> log.warn("Håndterer ikke filter for $feltkode. Legg til i ignorering hvis feltet håndteres spesielt.")
        }
    }

    override fun medBlokk(combineOperator: CombineOperator, defaultTrue: Boolean, blokk: () -> Unit) {
        whereClause += " ${combineOperator.sql} ("
        whereClause += defaultTrue.toString()
        whereClause += " "
        blokk()
        whereClause += ")"
    }

    override fun medEnkelOrder(feltområde: String?, feltkode: String, økende: Boolean) {
        if (feltområde != null) {
            medEnkelOrderAvOppgavefelt(feltområde, feltkode, økende)
            return
        }

        val retning = if (økende) "ASC" else "DESC"
        orderByClauses.add(
            when (feltkode) {
                "oppgavestatus" -> "o.oppgavestatus $retning"
                "oppgavetype" -> "o.oppgavetype_ekstern_id $retning"
                else -> throw IllegalStateException("Ukjent feltkode for sortering: $feltkode")
            }
        )
    }

    override fun mapRowTilId(row: Row): OppgaveId {
        return PartisjonertOppgaveId(row.long("id"))
    }

    override fun mapRowTilEksternId(row: Row): EksternOppgaveId {
        // område hardkodes siden det ikke er lagret
        return EksternOppgaveId("K9", row.string("oppgave_ekstern_id"))
    }

    private fun hentTransientFeltutleder(feltområde: String?, feltkode: String): TransientFeltutleder? {
        return felter[OmrådeOgKode(feltområde, feltkode)]?.transientFeltutleder
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

    private fun medOppgavefelt(
        combineOperator: CombineOperator,
        feltområde: String,
        feltkode: String,
        operator: FeltverdiOperator,
        feltverdi: List<Any?>
    ) {
        val index = queryParams.size + orderByParams.size
        val verdifelt = verdifelt(feltområde, feltkode)

        val (feltverdiPlaceholder, feltVerdiMap) = if (feltverdi.size > 1) {
            val prefix = "feltverdi${index * 1000}"
            "(${InClauseHjelper.tilParameternavn(feltverdi, prefix)})" to InClauseHjelper.parameternavnTilVerdierMap(
                feltverdi,
                prefix
            )
        } else {
            ":feltverdi$index" to mapOf("feltverdi$index" to feltverdi.first())
        }

        val negationPrefix = if (operator.negasjonAv != null) "NOT " else ""
        whereClause += """
             ${combineOperator.sql} ${negationPrefix}EXISTS (
                SELECT 1
                FROM oppgavefelt_verdi_part ov
                WHERE ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                  AND ov.oppgave_id = o.id
                  AND ov.feltdefinisjon_ekstern_id = :feltkode$index
                  AND $verdifelt ${operator.negasjonAv?.sql ?: operator.sql} $feltverdiPlaceholder
            )
        """.trimIndent()

        queryParams["feltkode$index"] = feltkode
        queryParams.putAll(feltVerdiMap)
    }

    private fun utenOppgavefelt(
        combineOperator: CombineOperator,
        feltkode: String,
        operator: FeltverdiOperator
    ) {
        val index = queryParams.size + orderByParams.size

        queryParams["feltkode$index"] = feltkode

        val invertertOperator = when (operator) {
            FeltverdiOperator.EQUALS, FeltverdiOperator.IN -> "NOT "
            FeltverdiOperator.NOT_EQUALS, FeltverdiOperator.NOT_IN -> ""
            else -> throw IllegalArgumentException("Ugyldig operator for tom verdi: $operator")
        }

        whereClause += """
            ${combineOperator.sql} ${invertertOperator}EXISTS (
                SELECT 1
                FROM oppgavefelt_verdi_part ov 
                WHERE ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                    AND ov.oppgave_id = o.id
                    AND ov.feltdefinisjon_ekstern_id = :feltkode$index
            )
        """.trimIndent()
    }

    private fun medEnkelOrderAvOppgavefelt(feltområde: String, feltkode: String, økende: Boolean) {
        hentTransientFeltutleder(feltområde, feltkode)?.let {
            val sqlMedParams = sikreUnikeParams(
                it.orderBy(OrderByInput(Spørringstrategi.PARTISJONERT, now, feltområde, feltkode, økende))
            )
            orderByClauses.add(sqlMedParams.query)
            orderByParams.putAll(sqlMedParams.queryParams)
            return
        }

        val index = queryParams.size + orderByParams.size
        val verdifelt = verdifelt(feltområde, feltkode)
        val retning = if (økende) "ASC" else "DESC"

        orderByParams["orderByfeltkode$index"] = feltkode

        orderByClauses.add(
            """
            (
              SELECT $verdifelt                    
              FROM oppgavefelt_verdi_part ov 
              WHERE ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                AND ov.oppgave_id = o.id
                AND ov.feltdefinisjon_ekstern_id = :orderByfeltkode$index
              LIMIT 1
            ) $retning
        """.trimIndent()
        )
    }

    private fun verdifelt(feltområde: String, feltkode: String): String {
        return when (oppgavefelterKodeOgType[OmrådeOgKode(feltområde, feltkode)]) {
            Datatype.INTEGER -> "ov.verdi_bigint"
            else -> "ov.verdi"
        }
    }

    // Select-felt støtte for effektive spørringer som returnerer OppgaveResultat
    private var selectFelter: List<EnkelSelectFelt> = emptyList()
    private val selectFeltParams: MutableMap<String, Any?> = mutableMapOf()

    override fun medSelectFelter(selectFelter: List<EnkelSelectFelt>) {
        this.selectFelter = selectFelter

        // Bygg ut SELECT-klausulen med subqueries for hvert felt
        val selectDeler = mutableListOf<String>()
        selectDeler.add("o.id")
        selectDeler.add("o.oppgave_ekstern_id")

        selectFelter.forEachIndexed { index, felt ->
            val alias = "felt_$index"

            if (felt.område != null) {
                // Felt fra oppgavefelt_verdi_part tabellen
                val verdifelt = verdifelt(felt.område, felt.kode)
                selectDeler.add("""
                    (SELECT $verdifelt
                     FROM oppgavefelt_verdi_part ov
                     WHERE ov.oppgave_id = o.id
                       AND ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                       AND ov.feltdefinisjon_ekstern_id = :selectFeltkode$index
                     LIMIT 1) AS $alias
                """.trimIndent())
                selectFeltParams["selectFeltkode$index"] = felt.kode
            } else {
                // Spesielle felt uten område - hentes direkte fra oppgavetabellen
                val kolonne = when (felt.kode) {
                    "oppgavestatus" -> "o.oppgavestatus"
                    "oppgavetype" -> "o.oppgavetype_ekstern_id"
                    else -> {
                        log.warn("Ukjent select-felt uten område: ${felt.kode}")
                        "NULL"
                    }
                }
                selectDeler.add("$kolonne AS $alias")
            }
        }

        selectClause = "SELECT " + selectDeler.joinToString(", ")
    }

    override fun getParams(): Map<String, Any?> {
        return buildMap {
            putAll(queryParams)
            putAll(orderByParams)
            putAll(oppgavestatusParams)
            putAll(ferdigstiltDatoParams)
            putAll(selectFeltParams)
        }
    }

    override fun mapRowTilOppgaveResultat(row: Row): OppgaveResultat {
        val eksternId = EksternOppgaveId("K9", row.string("oppgave_ekstern_id"))

        val feltverdier = selectFelter.mapIndexed { index, felt ->
            val alias = "felt_$index"
            val verdi: Any? = try {
                if (felt.område != null) {
                    val datatype = oppgavefelterKodeOgType[OmrådeOgKode(felt.område, felt.kode)]
                    when (datatype) {
                        Datatype.INTEGER -> row.longOrNull(alias)
                        else -> row.stringOrNull(alias)
                    }
                } else {
                    row.stringOrNull(alias)
                }
            } catch (e: Exception) {
                log.warn("Kunne ikke lese felt $alias: ${e.message}")
                null
            }

            Oppgavefeltverdi(
                område = felt.område,
                kode = felt.kode,
                verdi = verdi
            )
        }

        return OppgaveResultat(id = eksternId, felter = feltverdier)
    }
}