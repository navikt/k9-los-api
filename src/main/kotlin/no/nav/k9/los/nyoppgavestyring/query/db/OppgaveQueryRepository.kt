package no.nav.k9.los.nyoppgavestyring.query.db

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Kodeverkreferanse
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Verdiforklaring
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgaveQueryToSqlMapper
import no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder.GyldigeTransientFeltutleder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryRepository(
    val datasource: DataSource,
    val feltdefinisjonRepository: FeltdefinisjonRepository
) {
    private val log: Logger = LoggerFactory.getLogger("OppgaveQueryRepository")

    @WithSpan
    fun hentAlleFelter(): Oppgavefelter {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> Oppgavefelter(hentAlleFelter(tx)) }
        }
    }

    @WithSpan
    private fun hentAlleFelter(tx: TransactionalSession, medKodeverk: Boolean = true): List<Oppgavefelt> {
        return hentAlleFelterMedMer(tx, medKodeverk).map { it.oppgavefelt }
    }

    @WithSpan
    private fun hentAlleFelterMedMer(tx: TransactionalSession, medKodeverk: Boolean = true): List<OppgavefeltMedMer> {
        val felterFraDatabase = tx.run(
            queryOf(
                """
                    SELECT DISTINCT fo.ekstern_id as omrade,
                      fd.ekstern_id as kode,
                      fd.visningsnavn as visningsnavn,
                      fd.tolkes_som as tolkes_som,
                      fd.kokriterie as kokriterie,
                      fd.kodeverkreferanse as kodeverkreferanse,
                      fd.transient_feltutleder as transient_feltutleder
                    FROM Feltdefinisjon fd INNER JOIN Omrade fo ON (
                      fo.id = fd.omrade_id
                    )
                    WHERE fd.vis_til_bruker
                """.trimIndent()
            ).map { row ->
                val kodeverk = if (medKodeverk) {
                    row.stringOrNull("kodeverkreferanse")?.let {
                        feltdefinisjonRepository.hentKodeverk(Kodeverkreferanse(it), tx)
                    }
                } else {
                    null
                }
                OppgavefeltMedMer(
                    Oppgavefelt(
                        område = row.string("omrade"),
                        kode = row.string("kode"),
                        visningsnavn = row.string("visningsnavn"),
                        tolkes_som = row.string("tolkes_som"),
                        kokriterie = row.boolean("kokriterie"),
                        verdiforklaringerErUttømmende = kodeverk?.uttømmende ?: false,
                        verdiforklaringer = kodeverk?.let {
                            it.verdier.map { kodeverkverdi ->
                                Verdiforklaring(
                                    verdi = kodeverkverdi.verdi,
                                    visningsnavn = kodeverkverdi.visningsnavn,
                                    sekundærvalg = !kodeverkverdi.favoritt,
                                    gruppering = kodeverkverdi.gruppering
                                )
                            }
                        }
                    ),
                    row.stringOrNull("transient_feltutleder")?.let { GyldigeTransientFeltutleder.hentFeltutleder(it) }
                )
            }.asList
        )

        val oppgavetypeNavn = tx.run(
            queryOf(
                """
                    SELECT ot.ekstern_id FROM oppgavetype as ot 
                    """.trimIndent()
            ).map { it.string(1) }.asList
        )

        val standardfelter = listOf(
            Oppgavefelt(
                null,
                "oppgavestatus",
                "Oppgavestatus",
                "String",
                kokriterie = false,
                verdiforklaringerErUttømmende = true,
                verdiforklaringer = Oppgavestatus.entries.map { oppgavestatus ->
                    Verdiforklaring(
                        verdi = oppgavestatus.kode,
                        visningsnavn = oppgavestatus.visningsnavn,
                        sekundærvalg = false,
                        gruppering = null
                    )
                }
            ),
            Oppgavefelt(
                område = null,
                "sistEndret",
                "Tidspunkt siste endring",
                "Timestamp",
                kokriterie = false,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = listOf(),
            ),
            Oppgavefelt(
                område = null,
                kode = "personbeskyttelse",
                visningsnavn = "Kode 7 eller egen ansatt",
                tolkes_som = "String",
                kokriterie = false,
                verdiforklaringerErUttømmende = true,
                PersonBeskyttelseType.entries.map {
                    Verdiforklaring(
                        verdi = it.kode,
                        visningsnavn = it.beskrivelse,
                        sekundærvalg = false,
                        gruppering = null
                    )
                }
            ),
            Oppgavefelt(null, "oppgavetype", "Oppgavetype", "String", true, false,
                oppgavetypeNavn.map {
                    Verdiforklaring(
                        verdi = it,
                        visningsnavn = it,
                        sekundærvalg = false,
                        gruppering = null
                    )
                }
            ),
            Oppgavefelt(null, "spørringstrategi", "Spørringstrategi", "String", false, true, Spørringstrategi.entries.map { Verdiforklaring(
                it.name,
                it.navn,
                false,
                null
            ) }),
            Oppgavefelt(null, "ferdigstiltDato", "Ferdigstilt dato", "Timestamp", false, false, emptyList()),
        ).map { OppgavefeltMedMer(it, null) }

        return (felterFraDatabase + standardfelter).sortedBy { it.oppgavefelt.visningsnavn }
    }

    private fun queryForOppgaveId(tx: TransactionalSession, oppgaveQuery: OppgaveQuerySqlBuilder): List<OppgaveId> {
        loggSqlDebug(oppgaveQuery)

        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map(oppgaveQuery::mapRowTilId).asList
        )
    }

    private fun queryForEksternId(
        tx: TransactionalSession,
        oppgaveQuery: OppgaveQuerySqlBuilder
    ): List<EksternOppgaveId> {
        loggSqlDebug(oppgaveQuery)

        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map(oppgaveQuery::mapRowTilEksternId).asList
        )
    }

    @WithSpan
    fun query(
        request: QueryRequest,
        now: LocalDateTime
    ): OppgaveQueryResultat {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> query(tx, request, now) }
        }
    }

    @WithSpan
    fun query(
        tx: TransactionalSession,
        request: QueryRequest,
        now: LocalDateTime
    ): OppgaveQueryResultat {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associateBy { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) }

        val enkelSelectFelter = request.oppgaveQuery.select.filterIsInstance<EnkelSelectFelt>()
        val aggregerteFelter = request.oppgaveQuery.select.filterIsInstance<AggregertSelectFelt>()
        val oppgaveIdSelect = request.oppgaveQuery.select.singleOrNull() == OppgaveIdSelectFelt
        val eksternIdSelect = request.oppgaveQuery.select.singleOrNull() == EksternIdSelectFelt

        return when {
            oppgaveIdSelect -> {
                val sqlBuilder = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(request, felter, now)
                val ider = queryForOppgaveId(tx, sqlBuilder)
                OppgaveQueryResultat.OppgaveIdResultat(ider)
            }
            eksternIdSelect -> {
                val sqlBuilder = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(request, felter, now)
                val ider = queryForEksternId(tx, sqlBuilder)
                OppgaveQueryResultat.EksternIdResultat(ider)
            }
            aggregerteFelter.isNotEmpty() && enkelSelectFelter.isEmpty() && aggregerteFelter.size == 1 && aggregerteFelter[0] is AntallSelectFelt -> {
                val sqlBuilder = OppgaveQueryToSqlMapper.toSql(request, felter, now)
                val antall = tx.run(
                    queryOf(sqlBuilder.getQuery(), sqlBuilder.getParams())
                        .map { row -> row.long("agg_0") }.asSingle
                )!!
                OppgaveQueryResultat.AntallResultat(antall)
            }
            aggregerteFelter.isNotEmpty() -> {
                val sqlBuilder = OppgaveQueryToSqlMapper.toSql(request, felter, now)
                val rader = tx.run(
                    queryOf(sqlBuilder.getQuery(), sqlBuilder.getParams())
                        .map(sqlBuilder::mapRowTilGruppertResultat).asList
                )
                OppgaveQueryResultat.GruppertResultat(rader)
            }
            else -> {
                val sqlBuilder = OppgaveQueryToSqlMapper.toSql(request, felter, now)
                val rader = tx.run(
                    queryOf(sqlBuilder.getQuery(), sqlBuilder.getParams())
                        .map(sqlBuilder::mapRowTilOppgaveResultat).asList
                )
                OppgaveQueryResultat.SelectResultat(rader)
            }
        }
    }

    private fun loggSqlDebug(oppgaveQuery: OppgaveQuerySqlBuilder) {
        log.atLevel(Level.DEBUG)
            .setMessage("Kjører følgende intrapolerte SQL: \n{}")
            .addArgument { oppgaveQuery.unsafeDebug() }
            .log()
    }
}
