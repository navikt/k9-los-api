package no.nav.k9.los.nyoppgavestyring.query.db

import io.ktor.util.*
import io.opentelemetry.api.trace.StatusCode
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.kodeverk.BeskyttelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.EgenAnsatt
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Kodeverkreferanse
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Verdiforklaring
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgaveQueryToSqlMapper
import no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder.GyldigeTransientFeltutleder
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryRepository(
    val datasource: DataSource,
    val feltdefinisjonRepository: FeltdefinisjonRepository
) {
    private val log: Logger = LoggerFactory.getLogger("OppgaveQueryRepository")

    fun query(request: QueryRequest): List<AktivOppgaveId> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> query(tx, request, LocalDateTime.now()) }
        }
    }

    fun query(tx: TransactionalSession, request: QueryRequest, now: LocalDateTime): List<AktivOppgaveId> {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associate { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) to felt }

        return query(tx, OppgaveQueryToSqlMapper.toSqlOppgaveQuery(request, felter, now))
    }

    fun queryForEksternId(request: QueryRequest, now: LocalDateTime): List<EksternOppgaveId> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> queryForEksternId(tx, request, now) }
        }
    }

    fun queryForEksternId(tx: TransactionalSession, request: QueryRequest, now: LocalDateTime): List<EksternOppgaveId> {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associate { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) to felt }

        return queryForEksternId(tx, OppgaveQueryToSqlMapper.toSqlOppgaveQuery(request, felter, now))
    }

    fun queryForAntall(tx: TransactionalSession, oppgaveQuery: QueryRequest, now: LocalDateTime): Long {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associate { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) to felt }

        return queryForAntall(tx, OppgaveQueryToSqlMapper.toSqlOppgaveQueryForAntall(oppgaveQuery, felter, now))

    }

    private fun queryForAntall(tx: TransactionalSession, oppgaveQuery: OppgaveQuerySqlBuilder): Long {
        val query = oppgaveQuery.getQuery()
        val params = oppgaveQuery.getParams()

        val spanAttributes = mutableMapOf<String, String>()
        spanAttributes.put("preparedStmt", query.encodeBase64())
        for (param in oppgaveQuery.getParams()) {
            spanAttributes.put("param_" + param.key, if (param.value == null) "_null_" else param.value.toString() )
        }

        return OpentelemetrySpanUtil.span("queryForAntall", spanAttributes) {
            tx.run(
                queryOf(
                    query,
                    params
                ).map { row -> row.long("antall") }.asSingle
            )!!
        }
    }

    fun hentAlleFelter(): Oppgavefelter {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> Oppgavefelter(hentAlleFelter(tx)) }
        }
    }

    private fun hentAlleFelter(tx: TransactionalSession, medKodeverk: Boolean = true): List<Oppgavefelt> {
        return hentAlleFelterMedMer(tx, medKodeverk).map { it.oppgavefelt }
    }

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
                        verdiforklaringer = kodeverk?.let { kodeverk ->
                            kodeverk.verdier.map { kodeverkverdi ->
                                Verdiforklaring(
                                    verdi = kodeverkverdi.verdi,
                                    visningsnavn = kodeverkverdi.visningsnavn,
                                    sekundærvalg = !kodeverkverdi.favoritt
                                )
                            }
                        }
                    ),
                    row.stringOrNull("transient_feltutleder")?.let { GyldigeTransientFeltutleder.hentFeltutleder(it) }
                )
            }.asList
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
                        sekundærvalg = false
                    )
                }
            ),
            Oppgavefelt(
                område = null,
                kode = "beskyttelse",
                visningsnavn = "Beskyttelse",
                tolkes_som = "String",
                kokriterie = false,
                verdiforklaringerErUttømmende = true,
                BeskyttelseType.entries.map {
                    Verdiforklaring(
                        verdi = it.kode,
                        visningsnavn = it.beskrivelse,
                        sekundærvalg = false
                    )
                }
            ),
            Oppgavefelt(
                område = null,
                kode = "egenAnsatt",
                visningsnavn = "Egen ansatt",
                tolkes_som = "String",
                kokriterie = false,
                verdiforklaringerErUttømmende = true,
                EgenAnsatt.entries.map {
                    Verdiforklaring(
                        verdi = it.kode,
                        visningsnavn = it.beskrivelse,
                        sekundærvalg = false
                    )
                }
            ),
            Oppgavefelt(null, "kildeområde", "Kildeområde", "String", false, false, emptyList()),
            Oppgavefelt(null, "oppgavetype", "Oppgavetype", "String", true, false, emptyList()),
            Oppgavefelt(null, "oppgaveområde", "Oppgaveområde", "String", false, false, emptyList()),
        ).map { OppgavefeltMedMer(it, null) }

        return (felterFraDatabase + standardfelter).sortedBy { it.oppgavefelt.visningsnavn }
    }

    private fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuerySqlBuilder): List<AktivOppgaveId> {
        log.info("spørring oppgaveQuery for oppgaveId: ${oppgaveQuery.getQuery()}")
        /* val explain = tx.run(
            queryOf(
                "explain " + oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row ->
                row.string(1)
            }.asList
        ).joinToString("\n")
        log.info("explain oppgaveQuery for oppgaveId: $explain") */
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row -> AktivOppgaveId(row.long("id")) }.asList
        )
    }

    private fun queryForEksternId(
        tx: TransactionalSession,
        oppgaveQuery: OppgaveQuerySqlBuilder
    ): List<EksternOppgaveId> {
        log.info("spørring oppgaveQuery for oppgave EksternId: ${oppgaveQuery.getQuery()}")
        /*  val explain = tx.run(
            queryOf(
                "explain " + oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row ->
                row.string(1)
            }.asList
        ).joinToString("\n")
        log.info("explain oppgaveQuery for oppgaveId: $explain") */
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row ->
                EksternOppgaveId(
                    row.string("kildeomrade"),
                    row.string("ekstern_id")
                )
            }.asList
        )
    }
}