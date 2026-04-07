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
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AntallSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EksternIdSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveIdSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
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
        val oppgaveIdRequest = request.copy(
            oppgaveQuery = request.oppgaveQuery.copy(
                select = listOf(OppgaveIdSelectFelt),
            )
        )
        val resultat = oppgaveQueryRepository.query(tx, oppgaveIdRequest, now) as OppgaveQueryResultat.OppgaveIdResultat
        return resultat.ider.map { oppgaveId ->
            when (oppgaveId) {
                is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
                is PartisjonertOppgaveId -> partisjonertOppgaveRepository.hentOppgaveForId(oppgaveId, tx)
            }
        }
    }

    @WithSpan
    fun queryForAntall(request: QueryRequest, now: LocalDateTime = LocalDateTime.now()): Long {
        val antallRequest = request.copy(
            oppgaveQuery = request.oppgaveQuery.copy(
                select = listOf(AntallSelectFelt),
                order = emptyList(),
            )
        )
        val resultat = query(antallRequest, now)
        return (resultat as OppgaveQueryResultat.AntallResultat).antall
    }

    @WithSpan
    fun query(request: QueryRequest, now: LocalDateTime = LocalDateTime.now()): OppgaveQueryResultat {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> oppgaveQueryRepository.query(tx, request, now) }
        }
    }

    @WithSpan
    fun queryForOppgaveEksternId(request: QueryRequest): List<EksternOppgaveId> {
        val now = LocalDateTime.now()
        val eksternIdRequest = request.copy(
            oppgaveQuery = request.oppgaveQuery.copy(
                select = listOf(EksternIdSelectFelt),
            )
        )
        val resultat = query(eksternIdRequest, now) as OppgaveQueryResultat.EksternIdResultat
        return resultat.ider
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
