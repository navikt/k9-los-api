package no.nav.k9.los.nyoppgavestyring.query

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.PartisjonertOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveV3Id
import no.nav.k9.los.nyoppgavestyring.query.db.PartisjonertOppgaveId
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryRad
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryService(
    private val datasource: DataSource,
    private val oppgaveQueryRepository: OppgaveQueryRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val partisjonertOppgaveRepository: PartisjonertOppgaveRepository,
) {

    @WithSpan
    fun queryForOppgave(oppgaveQuery: QueryRequest): List<Oppgave> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> queryForOppgave(tx, oppgaveQuery) }
        }
    }

    @WithSpan
    fun queryForOppgave(tx: TransactionalSession, request: QueryRequest): List<Oppgave> {
        val now = LocalDateTime.now()
        val rader = oppgaveQueryRepository.query(tx, request, now)
        return rader.map { rad ->
            val oppgaveId = rad.oppgaveId
                ?: throw IllegalStateException("OppgaveQueryRad mangler oppgaveId. Dette kan skje for aggregerte spørringer.")
            when (oppgaveId) {
                is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
                is PartisjonertOppgaveId -> partisjonertOppgaveRepository.hentOppgaveForId(oppgaveId, tx)
            }
        }
    }

    @WithSpan
    fun queryForAntall(request: QueryRequest, now: LocalDateTime = LocalDateTime.now()): Long {
        val antallRequest = request.copy(
            oppgaveQuery = OppgaveQuery(
                filtere = request.oppgaveQuery.filtere,
                select = listOf(AggregertSelectFelt(Aggregeringsfunksjon.ANTALL)),
                order = emptyList(),
            )
        )
        val rader = query(antallRequest, now)
        return rader.first().aggregeringer.first().verdi as Long
    }

    @WithSpan
    fun query(request: QueryRequest, now: LocalDateTime = LocalDateTime.now()): List<OppgaveQueryRad> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> oppgaveQueryRepository.query(tx, request, now) }
        }
    }

    @WithSpan
    fun queryForOppgaveEksternId(request: QueryRequest): List<EksternOppgaveId> {
        val now = LocalDateTime.now()
        return using(sessionOf(datasource)) {
            it.transaction { tx ->
                oppgaveQueryRepository.query(tx, request, now).map { rad ->
                    rad.eksternOppgaveId
                        ?: throw IllegalStateException("OppgaveQueryRad mangler eksternOppgaveId. Dette kan skje for aggregerte spørringer.")
                }
            }
        }
    }

    @WithSpan
    fun hentAlleFelter(): Oppgavefelter {
        return oppgaveQueryRepository.hentAlleFelter()
    }

    fun validate(request: QueryRequest): Boolean {
        try {
            queryForAntall(request)
        } catch (_: RuntimeException) {
            return false
        }

        return true
    }
}
