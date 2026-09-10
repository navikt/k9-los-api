package no.nav.k9.los.forvaltning

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.kodeverk.Fagsystem

class ForvaltningRepository(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val transactionalManager: TransactionalManager,
) {

    fun hentAlleOppgavekoerMedQuery(tx: TransactionalSession, område: Områder): List<OppgaveKøMedQuery> {
        return tx.run(
            queryOf(
                """SELECT k.id, k.tittel, k.query FROM OPPGAVEKO_V3 k
                    JOIN omrade o ON o.id = k.omrade_id WHERE o.ekstern_id = :omrade""",
                mapOf("omrade" to område.eksternId)
            ).map { row ->
                OppgaveKøMedQuery(
                    id = row.long("id"),
                    tittel = row.string("tittel"),
                    oppgaveQuery = LosObjectMapper.instance.readValue(row.string("query"), OppgaveQuery::class.java)
                )
            }.asList
        )
    }

    fun hentAlleLagredeSøkMedQuery(tx: TransactionalSession, område: Områder): List<LagretSøkMedQuery> {
        return tx.run(
            queryOf(
                """
                SELECT ls.id, ls.tittel, s.epost as saksbehandler_epost, ls.query
                FROM lagret_sok ls
                INNER JOIN saksbehandler s ON ls.laget_av = s.id
                INNER JOIN omrade o ON o.id = ls.omrade_id
                WHERE o.ekstern_id = :omrade
                """.trimIndent(), mapOf("omrade" to område.eksternId)
            ).map { row ->
                LagretSøkMedQuery(
                    id = row.long("id"),
                    tittel = row.string("tittel"),
                    saksbehandlerEpost = row.string("saksbehandler_epost"),
                    oppgaveQuery = LosObjectMapper.instance.readValue(row.string("query"), OppgaveQuery::class.java)
                )
            }.asList
        )
    }

    internal fun krevEventområde(fagsystem: Fagsystem, område: Områder, eksternId: String? = null) {
        val (antall, feilOmråde) = transactionalManager.transaction { tx ->
            tx.run(queryOf(
                """SELECT count(*) AS antall,
                    count(*) FILTER (WHERE o.ekstern_id IS DISTINCT FROM :omrade) AS feil_omrade
                    FROM event_nokkel en
                    LEFT JOIN omrade o ON o.id = en.omrade_id
                    WHERE en.fagsystem = :fagsystem
                    AND (:eksternId::text IS NULL OR en.ekstern_id = :eksternId)""",
                mapOf("fagsystem" to fagsystem.kode, "eksternId" to eksternId, "omrade" to område.eksternId)
            ).map { it.long("antall") to it.long("feil_omrade") }.asSingle)
                ?: throw SecurityException("Eventområdet kan ikke fastslås")
        }
        if ((eksternId != null && antall == 0L) || feilOmråde > 0) {
            throw SecurityException("Ingen tilgang til eventressurs")
        }
    }
}
