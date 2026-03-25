package no.nav.k9.los.nyoppgavestyring.query

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Id
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.PartisjonertOppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.PartisjonertOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AntallSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.koin.java.KoinJavaComponent.inject
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryService {
    private val datasource by inject<DataSource>(DataSource::class.java)
    private val oppgaveQueryRepository by inject<OppgaveQueryRepository>(OppgaveQueryRepository::class.java)
    private val oppgaveRepository by inject<OppgaveRepository>(OppgaveRepository::class.java)
    private val partisjonertOppgaveRepository by inject<PartisjonertOppgaveRepository>(PartisjonertOppgaveRepository::class.java)

    @WithSpan
    fun queryForOppgave(oppgaveQuery: QueryRequest): List<Oppgave> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> queryForOppgave(tx, oppgaveQuery) }
        }
    }

    @WithSpan
    fun queryForOppgave(tx: TransactionalSession, oppgaveQuery: QueryRequest): List<Oppgave> {
        val now = LocalDateTime.now()
        val oppgaveIder = oppgaveQueryRepository.query(tx, oppgaveQuery, LocalDateTime.now())
        return oppgaveIder.map { oppgaveId ->
            when (oppgaveId) {
                is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
                is PartisjonertOppgaveId -> partisjonertOppgaveRepository.hentOppgaveForId(oppgaveId, tx)
            }
        }
    }

    @WithSpan
    fun queryForAntall(request: QueryRequest, now: LocalDateTime = LocalDateTime.now()): Long {
        val antallRequest = if (request.oppgaveQuery.select.any { it is AntallSelectFelt }) {
            request
        } else {
            request.copy(oppgaveQuery = request.oppgaveQuery.copy(select = listOf(AntallSelectFelt())))
        }
        val resultat = queryMedSelect(antallRequest, now)
        return (resultat as OppgaveQueryResultat.AntallResultat).antall
    }

    @WithSpan
    fun queryMedSelect(request: QueryRequest, now: LocalDateTime = LocalDateTime.now()): OppgaveQueryResultat {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> oppgaveQueryRepository.queryMedSelect(tx, request, now) }
        }
    }

    @WithSpan
    fun queryForOppgaveEksternId(oppgaveQuery: QueryRequest): List<EksternOppgaveId> {
        val now = LocalDateTime.now()
        return oppgaveQueryRepository.queryForEksternId(oppgaveQuery, now)
    }

    @WithSpan
    fun queryForOppgaveResultat(request: QueryRequest): List<OppgaveResultat> {
        return using(sessionOf(datasource)) {
            it.transaction { tx ->
                queryForOppgaveResultat(tx, request)
            }
        }
    }

    @WithSpan
    fun queryForOppgaveResultat(tx: TransactionalSession, request: QueryRequest): List<OppgaveResultat> {
        val now = LocalDateTime.now()
        val resultat = oppgaveQueryRepository.queryMedSelect(tx, request, now)
        return (resultat as OppgaveQueryResultat.SelectResultat).rader
    }



    @WithSpan
    fun hentAlleFelter(): Oppgavefelter {
        return oppgaveQueryRepository.hentAlleFelter()
    }



    fun validate(request: QueryRequest): Boolean {
        try {
            queryForAntall(request)
        } catch (e: RuntimeException) {
            return false
        }

        return true
    }
}