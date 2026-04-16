package no.nav.k9.los.nyoppgavestyring.query.db

import com.fasterxml.jackson.core.type.TypeReference
import kotliquery.Row
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Aggregertverdi
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.GruppertOppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.query.mapping.*
import no.nav.k9.los.nyoppgavestyring.spi.felter.*
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
    private var groupByClause = ""
    private var pagingClause = ""
    private val grupperingsAlias = mutableMapOf<OmrådeOgKode, String>()
    private val aggregeringsOrderByUttrykk = mutableMapOf<Int, String>()

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
        return "$selectClause $fromClause $whereClause $groupByClause $orderByClause $pagingClause"
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

            "reservasjonsnokkel" -> {
                val (feltverdiPlaceholder, feltVerdiMap) = if (feltverdier.size > 1) {
                    val prefix = "reservasjonsnokkel${index * 1000}"
                    "(${
                        InClauseHjelper.tilParameternavn(
                            feltverdier,
                            prefix
                        )
                    })" to InClauseHjelper.parameternavnTilVerdierMap(feltverdier, prefix)
                } else {
                    ":reservasjonsnokkel$index" to mapOf("reservasjonsnokkel$index" to feltverdier.first())
                }

                whereClause += " ${combineOperator.sql} o.reservasjonsnokkel ${operator.sql} $feltverdiPlaceholder"
                queryParams.putAll(feltVerdiMap)
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
        if (aggregerteFelter.isNotEmpty()) {
            val alias = grupperingsAlias[OmrådeOgKode(feltområde, feltkode)]
                ?: throw IllegalStateException("Kan ikke sortere gruppert query på felt som ikke er en del av grupperingen: ${feltområde ?: "null"}.$feltkode")
            val retning = if (økende) "ASC" else "DESC"
            orderByClauses.add("$alias $retning")
            return
        }

        if (feltområde != null) {
            medEnkelOrderAvOppgavefelt(feltområde, feltkode, økende)
            return
        }

        val retning = if (økende) "ASC" else "DESC"
        orderByClauses.add(
            when (feltkode) {
                "oppgavestatus" -> "o.oppgavestatus $retning"
                "oppgavetype" -> "o.oppgavetype_ekstern_id $retning"
                "ferdigstiltDato" -> "o.ferdigstilt_dato $retning"
                "reservasjonsnokkel" -> "o.reservasjonsnokkel $retning"
                else -> throw IllegalStateException("Ukjent feltkode for sortering: $feltkode")
            }
        )
    }

    override fun medAggregertOrder(
        funksjon: Aggregeringsfunksjon,
        feltområde: String?,
        feltkode: String?,
        økende: Boolean
    ) {
        if (aggregerteFelter.isEmpty()) {
            throw IllegalStateException("Kan ikke sortere på aggregert felt uten aggregering.")
        }

        val matchendeAggregeringer = aggregerteFelter.mapIndexedNotNull { index, felt ->
            if (felt.funksjon == funksjon && felt.område == feltområde && felt.kode == feltkode) index to felt else null
        }

        require(matchendeAggregeringer.isNotEmpty()) {
            "Fant ingen aggregert felt for sortering: $funksjon ${feltkode ?: "(uten felt)"}"
        }

        require(matchendeAggregeringer.size == 1) {
            "Aggregert sortering er tvetydig for: $funksjon ${feltkode ?: "(uten felt)"}"
        }

        val (index, felt) = matchendeAggregeringer.single()
        val retning = if (økende) "ASC" else "DESC"

        val orderByUttrykk = aggregeringsOrderByUttrykk[index] ?: run {
            val (aggregeringsuttrykk, params) = byggAggregeringsuttrykk(felt, index)
            orderByParams.putAll(params)
            lagAggregeringsOrderByUttrykk(felt, aggregeringsuttrykk)
        }

        orderByClauses.add("$orderByUttrykk $retning")
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

    private fun erListetype(feltområde: String, feltkode: String): Boolean {
        return felter[OmrådeOgKode(feltområde, feltkode)]?.oppgavefelt?.listetype ?: false
    }

    private data class Aggregeringsgrunnlag(
        val uttrykk: String,
        val datatype: Datatype,
        val queryParams: Map<String, Any?> = emptyMap(),
    )

    private fun datatypeForFelt(feltområde: String?, feltkode: String): Datatype {
        return oppgavefelterKodeOgType[OmrådeOgKode(feltområde, feltkode)]
            ?: throw IllegalStateException("Fant ikke datatype for aggregeringsfelt ${feltområde ?: "null"}.$feltkode")
    }

    private fun validerStøttetAggregering(
        funksjon: Aggregeringsfunksjon,
        datatype: Datatype,
        feltkode: String,
    ) {
        when (funksjon) {
            Aggregeringsfunksjon.ANTALL -> Unit
            Aggregeringsfunksjon.SUM, Aggregeringsfunksjon.GJENNOMSNITT -> require(datatype == Datatype.INTEGER) {
                "Aggregeringsfunksjon $funksjon støttes kun for heltallsfelt. Felt $feltkode er ${datatype.kode}."
            }

            Aggregeringsfunksjon.MIN, Aggregeringsfunksjon.MAKS -> require(datatype != Datatype.DURATION) {
                "Aggregeringsfunksjon $funksjon støttes ikke for Duration-felt ennå. Felt $feltkode er ${datatype.kode}."
            }
        }
    }

    private fun kolonneForAggregeringsfeltUtenOmråde(feltkode: String): String {
        return when (feltkode) {
            "oppgavestatus" -> "o.oppgavestatus"
            "oppgavetype" -> "o.oppgavetype_ekstern_id"
            "ferdigstiltDato" -> "o.ferdigstilt_dato"
            "reservasjonsnokkel" -> "o.reservasjonsnokkel"
            "sistEndret" -> "o.endret_tidspunkt"
            else -> throw IllegalStateException("Ukjent aggregeringsfelt uten område: $feltkode")
        }
    }

    private fun castOppgavefeltuttrykkTilDatatype(uttrykk: String, datatype: Datatype): String {
        return when (datatype) {
            Datatype.INTEGER, Datatype.STRING -> uttrykk
            Datatype.TIMESTAMP -> "CAST($uttrykk AS timestamp)"
            Datatype.BOOLEAN -> "CAST($uttrykk AS boolean)"
            Datatype.DURATION -> throw IllegalArgumentException("Aggregering støttes ikke for Duration-felt ennå.")
        }
    }

    private fun byggAggregeringsgrunnlag(felt: AggregertSelectFelt, index: Int): Aggregeringsgrunnlag {
        val feltKode = requireNotNull(felt.kode) { "AggregertSelectFelt ${felt.funksjon} mangler kode" }
        val datatype = datatypeForFelt(felt.område, feltKode)
        validerStøttetAggregering(felt.funksjon, datatype, feltKode)

        if (felt.område == null) {
            return Aggregeringsgrunnlag(
                uttrykk = kolonneForAggregeringsfeltUtenOmråde(feltKode),
                datatype = datatype
            )
        }

        val transientFeltutleder = hentTransientFeltutleder(felt.område, feltKode)
        if (transientFeltutleder != null) {
            val sqlMedParams = sikreUnikeParams(
                transientFeltutleder.select(
                    SelectInput(
                        Spørringstrategi.PARTISJONERT,
                        now,
                        felt.område,
                        feltKode
                    )
                )
            )
            return Aggregeringsgrunnlag(
                uttrykk = castOppgavefeltuttrykkTilDatatype(sqlMedParams.query, datatype),
                datatype = datatype,
                queryParams = sqlMedParams.queryParams
            )
        }

        val verdifelt = verdifelt(felt.område, feltKode)
        val baseUttrykk = """
            (SELECT $verdifelt
             FROM oppgavefelt_verdi_part ov
             WHERE ov.oppgave_id = o.id
               AND ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
               AND ov.feltdefinisjon_ekstern_id = :aggFeltkode$index
             LIMIT 1)
        """.trimIndent()

        return Aggregeringsgrunnlag(
            uttrykk = castOppgavefeltuttrykkTilDatatype(baseUttrykk, datatype),
            datatype = datatype,
            queryParams = mapOf("aggFeltkode$index" to feltKode)
        )
    }

    private fun byggAggregeringsuttrykk(
        felt: AggregertSelectFelt,
        index: Int,
    ): Pair<String, Map<String, Any?>> {
        if (felt.funksjon == Aggregeringsfunksjon.ANTALL) return "COUNT(*)" to emptyMap()

        val grunnlag = byggAggregeringsgrunnlag(felt, index)

        return when (felt.funksjon) {
            Aggregeringsfunksjon.GJENNOMSNITT -> "AVG(${grunnlag.uttrykk})"
            Aggregeringsfunksjon.SUM -> "SUM(${grunnlag.uttrykk})"
            Aggregeringsfunksjon.MIN -> "MIN(${grunnlag.uttrykk})"
            Aggregeringsfunksjon.MAKS -> "MAX(${grunnlag.uttrykk})"
        } to grunnlag.queryParams
    }

    private fun erNumeriskAggregering(felt: AggregertSelectFelt): Boolean {
        val feltKode = felt.kode ?: return false
        return datatypeForFelt(felt.område, feltKode) == Datatype.INTEGER
    }

    private fun lagAggregeringsOrderByUttrykk(
        felt: AggregertSelectFelt,
        aggregeringsuttrykk: String,
    ): String {
        return when {
            felt.funksjon in setOf(Aggregeringsfunksjon.ANTALL, Aggregeringsfunksjon.SUM, Aggregeringsfunksjon.GJENNOMSNITT) ->
                "CAST(($aggregeringsuttrykk) AS numeric)"

            felt.funksjon in setOf(Aggregeringsfunksjon.MIN, Aggregeringsfunksjon.MAKS) && erNumeriskAggregering(felt) ->
                "CAST(($aggregeringsuttrykk) AS numeric)"

            else -> "($aggregeringsuttrykk)"
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
                val transientFeltutleder = hentTransientFeltutleder(felt.område, felt.kode)
                if (transientFeltutleder != null) {
                    val sqlMedParams = sikreUnikeParams(
                        transientFeltutleder.select(
                            SelectInput(
                                Spørringstrategi.PARTISJONERT,
                                now,
                                felt.område,
                                felt.kode
                            )
                        )
                    )
                    selectDeler.add("${sqlMedParams.query} AS $alias")
                    selectFeltParams.putAll(sqlMedParams.queryParams)
                } else {
                    val verdifelt = verdifelt(felt.område, felt.kode)
                    if (erListetype(felt.område, felt.kode)) {
                        selectDeler.add("""
                            (SELECT json_agg(ov.verdi ORDER BY ov.verdi)
                             FROM oppgavefelt_verdi_part ov
                             WHERE ov.oppgave_id = o.id
                               AND ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                               AND ov.feltdefinisjon_ekstern_id = :selectFeltkode$index
                            ) AS $alias
                        """.trimIndent())
                    } else {
                        selectDeler.add("""
                            (SELECT $verdifelt
                             FROM oppgavefelt_verdi_part ov
                             WHERE ov.oppgave_id = o.id
                               AND ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                               AND ov.feltdefinisjon_ekstern_id = :selectFeltkode$index
                             LIMIT 1) AS $alias
                        """.trimIndent())
                    }
                    selectFeltParams["selectFeltkode$index"] = felt.kode
                }
            } else {
                val kolonne = when (felt.kode) {
                    "oppgavestatus" -> "o.oppgavestatus"
                    "oppgavetype" -> "o.oppgavetype_ekstern_id"
                    "ferdigstiltDato" -> "o.ferdigstilt_dato"
                    "reservasjonsnokkel" -> "o.reservasjonsnokkel"
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


    // Gruppering-støtte for COUNT(*) ... GROUP BY queries
    private var grupperingsFelter: List<EnkelSelectFelt> = emptyList()
    private var aggregerteFelter: List<AggregertSelectFelt> = emptyList()
    private val grupperingParams: MutableMap<String, Any?> = mutableMapOf()

    override fun medGruppering(grupperingsFelter: List<EnkelSelectFelt>, aggregerteFelter: List<AggregertSelectFelt>) {
        this.grupperingsFelter = grupperingsFelter
        this.aggregerteFelter = aggregerteFelter
        grupperingsAlias.clear()
        aggregeringsOrderByUttrykk.clear()

        val selectDeler = mutableListOf<String>()
        val groupByDeler = mutableListOf<String>()
        val joinDeler = mutableListOf<String>()

        grupperingsFelter.forEachIndexed { index, felt ->
            val alias = "gruppe_$index"

            if (felt.område != null) {
                val transientFeltutleder = hentTransientFeltutleder(felt.område, felt.kode)
                if (transientFeltutleder != null) {
                    val sqlMedParams = sikreUnikeParams(
                        transientFeltutleder.select(
                            SelectInput(
                                Spørringstrategi.PARTISJONERT,
                                now,
                                felt.område,
                                felt.kode
                            )
                        )
                    )
                    selectDeler.add("${sqlMedParams.query} AS $alias")
                    grupperingParams.putAll(sqlMedParams.queryParams)
                } else {
                    val verdifelt = verdifelt(felt.område, felt.kode)
                    if (erListetype(felt.område, felt.kode)) {
                        val joinAlias = "ov_gruppe_$index"
                        joinDeler.add(
                            """
                            JOIN oppgavefelt_verdi_part $joinAlias
                              ON $joinAlias.oppgave_id = o.id
                              AND $joinAlias.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse(joinAlias)}
                              AND $joinAlias.feltdefinisjon_ekstern_id = :grupperingFeltkode$index
                        """.trimIndent()
                        )
                        selectDeler.add("$joinAlias.verdi AS $alias")
                    } else {
                        selectDeler.add(
                            """
                            (SELECT $verdifelt
                             FROM oppgavefelt_verdi_part ov
                             WHERE ov.oppgave_id = o.id
                               AND ov.oppgavestatus IN ($oppgavestatusPlaceholder) ${ferdigstiltDatoBetingelse("ov")}
                               AND ov.feltdefinisjon_ekstern_id = :grupperingFeltkode$index
                             LIMIT 1) AS $alias
                        """.trimIndent()
                        )
                    }
                    grupperingParams["grupperingFeltkode$index"] = felt.kode
                }
            } else {
                val kolonne = when (felt.kode) {
                    "oppgavestatus" -> "o.oppgavestatus"
                    "oppgavetype" -> "o.oppgavetype_ekstern_id"
                    "ferdigstiltDato" -> "o.ferdigstilt_dato"
                    "reservasjonsnokkel" -> "o.reservasjonsnokkel"
                    else -> {
                        log.warn("Ukjent grupperings-felt uten område: ${felt.kode}")
                        "NULL"
                    }
                }
                selectDeler.add("$kolonne AS $alias")
            }
            grupperingsAlias[OmrådeOgKode(felt.område, felt.kode)] = alias
            groupByDeler.add(alias)
        }

        if (joinDeler.isNotEmpty()) {
            fromClause += " " + joinDeler.joinToString(" ")
        }

        aggregerteFelter.forEachIndexed { index, felt ->
            val alias = "agg_$index"
            val (uttrykk, params) = byggAggregeringsuttrykk(felt, index)
            grupperingParams.putAll(params)
            selectDeler.add("($uttrykk)::text AS $alias")
            aggregeringsOrderByUttrykk[index] = lagAggregeringsOrderByUttrykk(felt, uttrykk)
        }

        selectClause = "SELECT " + selectDeler.joinToString(", ")
        if (groupByDeler.isNotEmpty()) {
            groupByClause = "GROUP BY " + groupByDeler.joinToString(", ")
        }
        orderByClauses.clear()
        orderByParams.clear()
    }

    override fun mapRowTilGruppertResultat(row: Row): GruppertOppgaveResultat {
        val grupperingsverdier = grupperingsFelter.mapIndexed { index, felt ->
            val alias = "gruppe_$index"
            Oppgavefeltverdi(
                område = felt.område,
                kode = felt.kode,
                verdi = row.stringOrNull(alias)
            )
        }
        val aggregeringer = aggregerteFelter.mapIndexed { index, felt ->
            val alias = "agg_$index"
            Aggregertverdi(
                type = felt.funksjon,
                område = felt.område,
                kode = felt.kode,
                verdi = row.stringOrNull(alias)
            )
        }
        return GruppertOppgaveResultat(
            grupperingsverdier = grupperingsverdier,
            aggregeringer = aggregeringer,
        )
    }

    override fun getParams(): Map<String, Any?> {
        return buildMap {
            putAll(queryParams)
            putAll(orderByParams)
            putAll(oppgavestatusParams)
            putAll(ferdigstiltDatoParams)
            putAll(selectFeltParams)
            putAll(grupperingParams)
        }
    }

    override fun mapRowTilOppgaveResultat(row: Row): OppgaveResultat {
        val eksternId = EksternOppgaveId("K9", row.string("oppgave_ekstern_id"))

        val feltverdier = selectFelter.mapIndexed { index, felt ->
            val alias = "felt_$index"
            val verdi: Any? = if (felt.område != null && erListetype(felt.område, felt.kode)) {
                row.stringOrNull(alias)?.let { jsonArray ->
                    LosObjectMapper.instance.readValue(jsonArray, object : TypeReference<List<String>>() {})
                } ?: emptyList<String>()
            } else {
                row.stringOrNull(alias)
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
