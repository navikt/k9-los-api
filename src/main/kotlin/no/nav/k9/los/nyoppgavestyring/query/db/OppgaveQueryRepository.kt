package no.nav.k9.los.nyoppgavestyring.query.db

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Kodeverkreferanse
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Verdiforklaring
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgaveQueryToSqlMapper
import no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder.GyldigeTransientFeltutleder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryRepository(
    val datasource: DataSource,
    val feltdefinisjonRepository: FeltdefinisjonRepository
) {
    private val log: Logger = LoggerFactory.getLogger("OppgaveQueryRepository")

    @WithSpan
    fun query(request: QueryRequest): List<OppgaveId> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> query(tx, request, LocalDateTime.now()) }
        }
    }

    @WithSpan
    fun query(tx: TransactionalSession, request: QueryRequest, now: LocalDateTime): List<OppgaveId> {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associateBy { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) }

        return query(tx, OppgaveQueryToSqlMapper.toSqlOppgaveQuery(request, felter, now))
    }

    @WithSpan
    fun queryForEksternId(request: QueryRequest, now: LocalDateTime): List<EksternOppgaveId> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> queryForEksternId(tx, request, now) }
        }
    }

    @WithSpan
    fun queryForEksternId(tx: TransactionalSession, request: QueryRequest, now: LocalDateTime): List<EksternOppgaveId> {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associate { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) to felt }

        return queryForEksternId(tx, OppgaveQueryToSqlMapper.toSqlOppgaveQuery(request, felter, now))
    }

    @WithSpan
    fun queryForAntall(tx: TransactionalSession, oppgaveQuery: QueryRequest, now: LocalDateTime): Long {
        val felter = hentAlleFelterMedMer(tx, medKodeverk = false)
            .associate { felt -> OmrådeOgKode(felt.oppgavefelt.område, felt.oppgavefelt.kode) to felt }

        return queryForAntall(tx, OppgaveQueryToSqlMapper.toSqlOppgaveQueryForAntall(oppgaveQuery, felter, now))

    }

    @WithSpan
    private fun queryForAntall(tx: TransactionalSession, oppgaveQuery: OppgaveQuerySqlBuilder): Long {
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row -> row.long("antall") }.asSingle
        )!!
    }

    @WithSpan
    fun hentAlleFelter(): Oppgavefelter {
        return using(sessionOf(datasource)) { it ->
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
            Oppgavefelt(null, "oppgavetype", "Oppgavetype", "String", false, false,
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

    private fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuerySqlBuilder): List<OppgaveId> {
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
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map(oppgaveQuery::mapRowTilEksternId).asList
        )
    }
}