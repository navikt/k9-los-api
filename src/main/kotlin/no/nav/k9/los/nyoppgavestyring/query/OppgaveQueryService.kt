package no.nav.k9.los.nyoppgavestyring.query

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Auditlogging
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.saksbehandler.IIdToken
import org.koin.java.KoinJavaComponent.inject
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryService {
    private val datasource by inject<DataSource>(DataSource::class.java)
    private val oppgaveQueryRepository by inject<OppgaveQueryRepository>(OppgaveQueryRepository::class.java)
    private val aktivOppgaveRepository by inject<AktivOppgaveRepository>(AktivOppgaveRepository::class.java)
    private val oppgaveRepository by inject<OppgaveRepository>(OppgaveRepository::class.java)
    private val partisjonertOppgaveRepository by inject<PartisjonertOppgaveRepository>(PartisjonertOppgaveRepository::class.java)
    private val pepClient by inject<IPepClient>(IPepClient::class.java)

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
                is AktivOppgaveId -> aktivOppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
                is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
                is PartisjonertOppgaveId ->
                    partisjonertOppgaveRepository.hentOppgaveEksternIdOgOppgavetype(oppgaveId, tx)
                        .let { (oppgaveEksternId, oppgavetypeEksternId) ->
                            oppgaveRepository.hentOppgaveForEksternIdOgOppgavetype(
                                tx,
                                oppgaveEksternId,
                                oppgavetypeEksternId
                            )
                        }
            }
        }
    }

    @WithSpan
    fun queryForAntall(request: QueryRequest, now : LocalDateTime = LocalDateTime.now()): Long {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> oppgaveQueryRepository.queryForAntall(tx, request, now) }
        }
    }

    @WithSpan
    fun queryForOppgaveEksternId(oppgaveQuery: QueryRequest): List<EksternOppgaveId> {
        val now = LocalDateTime.now()
        return oppgaveQueryRepository.queryForEksternId(oppgaveQuery, now)
    }
    @WithSpan
    fun queryToFile(oppgaveQuery: QueryRequest, idToken: IIdToken): String {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> queryToFile(tx, oppgaveQuery, idToken) }
        }
    }

    @WithSpan
    fun queryToFile(tx: TransactionalSession, oppgaveQuery: QueryRequest, idToken: IIdToken): String {
        val oppgaver = query(tx, oppgaveQuery, idToken)
        if (oppgaver.isEmpty()) {
            return ""
        }

        val oppgaverad = oppgaver[0]
        val oppgavefelter = oppgaveQueryRepository.hentAlleFelter().felter.associateBy {
            it.område + it.kode
        }

        val header = oppgaverad.joinToString(";") { oppgavefelter[it.område + it.kode]?.visningsnavn ?: "" }

        return header + "\n" + oppgaver.joinToString("\n") { or: Oppgaverad ->
            or.joinToString(";") {
                if (it.verdi == null) "" else it.verdi.toString()
            }
        }
    }

    @WithSpan
    fun query(oppgaveQuery: QueryRequest, idToken: IIdToken): List<Oppgaverad> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> query(tx, oppgaveQuery, idToken) }
        }
    }

    @WithSpan
    fun query(tx: TransactionalSession, request: QueryRequest, idToken: IIdToken): List<Oppgaverad> {
        val now = LocalDateTime.now()
        val oppgaveIder = oppgaveQueryRepository.query(tx, request, now)

        val oppgaverader = runBlocking(context = CoroutineRequestContext(idToken)) {
            mapOppgaver(tx, request, oppgaveIder, now)
        }

        if (request.oppgaveQuery.select.isEmpty()) {
            return listOf(listOf(Oppgavefeltverdi(null, "Antall", oppgaverader.size)))
        }

        return oppgaverader
    }

    @WithSpan
    fun hentAlleFelter(): Oppgavefelter {
        return oppgaveQueryRepository.hentAlleFelter()
    }

    @WithSpan
    private suspend fun mapOppgaver(tx: TransactionalSession, request: QueryRequest, oppgaveIder: List<OppgaveId>, now: LocalDateTime): List<Oppgaverad> {
        val oppgaverader = mutableListOf<Oppgaverad>()
        val limit = request.avgrensning?.limit ?: -1
        var antall = 0
        for (oppgaveId in oppgaveIder) {
            val oppgaverad = mapOppgave(tx, request.oppgaveQuery, oppgaveId, now)
            if (oppgaverad != null) {
                oppgaverader.add(oppgaverad)
                antall++
                if (limit in 0..antall) {
                    return oppgaverader
                }
            }
        }
        return oppgaverader
    }

    @WithSpan
    private suspend fun mapOppgave(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, oppgaveId: OppgaveId, now: LocalDateTime): Oppgaverad? {
        val oppgave = when (oppgaveId) {
            is AktivOppgaveId -> aktivOppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
            is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)
            is PartisjonertOppgaveId -> partisjonertOppgaveRepository.hentOppgaveEksternIdOgOppgavetype(oppgaveId, tx)
                .let { (oppgaveEksternId, oppgavetypeEksternId) ->
                    oppgaveRepository.hentOppgaveForEksternIdOgOppgavetype(
                        tx,
                        oppgaveEksternId,
                        oppgavetypeEksternId
                    )
                }
        }

        if (!pepClient.harTilgangTilOppgaveV3(oppgave = oppgave, action = Action.read, auditlogging = Auditlogging.LOGG_VED_PERMIT)) {
            return null
        }

        return if (oppgaveQuery.select.isEmpty()) {
            emptyList()
        } else {
            return toOppgavefeltverdier(oppgaveQuery, oppgave)
        }
    }

    private fun toOppgavefeltverdier(
        oppgaveQuery: OppgaveQuery,
        oppgave: Oppgave
    ) = oppgaveQuery.select.map {
        if (it is EnkelSelectFelt) {
            val verdi = when (it.kode) {
                "oppgavestatus" -> oppgave.status
                "kildeområde" -> oppgave.kildeområde
                "oppgavetype" -> oppgave.oppgavetype.eksternId
                "oppgaveområde" -> oppgave.oppgavetype.område.eksternId
                else -> oppgave.hentVerdiEllerListe(requireNotNull(it.område), it.kode)
            }
            Oppgavefeltverdi(
                it.område,
                it.kode,
                verdi
            )
        } else {
            // TODO: Støtt aggregerte felter:
            Oppgavefeltverdi("", "", "")
        }
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