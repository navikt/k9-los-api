package no.nav.k9.los.nyoppgavestyring.forvaltning

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.uthenting.query.dto.query.OppgaveQuery

class ForvaltningRepository(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val transactionalManager: TransactionalManager,
) {

    fun hentAlleOppgavekoerMedQuery(tx: TransactionalSession): List<OppgaveKøMedQuery> {
        return tx.run(
            queryOf(
                """SELECT id, tittel, query FROM OPPGAVEKO_V3"""
            ).map { row ->
                OppgaveKøMedQuery(
                    id = row.long("id"),
                    tittel = row.string("tittel"),
                    oppgaveQuery = LosObjectMapper.instance.readValue(row.string("query"), OppgaveQuery::class.java)
                )
            }.asList
        )
    }

    fun hentAlleLagredeSøkMedQuery(tx: TransactionalSession): List<LagretSøkMedQuery> {
        return tx.run(
            queryOf(
                """
                SELECT ls.id, ls.tittel, s.epost as saksbehandler_epost, ls.query
                FROM lagret_sok ls
                INNER JOIN saksbehandler s ON ls.laget_av = s.id
                """.trimIndent()
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
}