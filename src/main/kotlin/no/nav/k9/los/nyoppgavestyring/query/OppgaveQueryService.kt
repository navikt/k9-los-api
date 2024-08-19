package no.nav.k9.los.nyoppgavestyring.query

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.Auditlogging
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.CoroutineRequestContext
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
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
import java.lang.RuntimeException
import java.time.LocalDateTime
import javax.sql.DataSource

class OppgaveQueryService() {
    val datasource by inject<DataSource>(DataSource::class.java)
    val oppgaveQueryRepository by inject<OppgaveQueryRepository>(OppgaveQueryRepository::class.java)
    val aktivOppgaveRepository by inject<AktivOppgaveRepository>(AktivOppgaveRepository::class.java)
    val oppgaveRepository by inject<OppgaveRepository>(OppgaveRepository::class.java)
    val pepClient by inject<IPepClient>(IPepClient::class.java)

    fun queryForOppgaveId(oppgaveQuery: QueryRequest): List<AktivOppgaveId> {
        return oppgaveQueryRepository.query(oppgaveQuery)
    }

    fun queryForOppgaveId(tx: TransactionalSession, oppgaveQuery: QueryRequest): List<AktivOppgaveId> {
        return oppgaveQueryRepository.query(tx, oppgaveQuery, LocalDateTime.now())
    }

    fun queryForAntall(request: QueryRequest, now : LocalDateTime = LocalDateTime.now()): Long {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> oppgaveQueryRepository.queryForAntall(tx, request, now) }
        }
    }

    fun queryForOppgaveEksternId(oppgaveQuery: QueryRequest): List<EksternOppgaveId> {
        val now = LocalDateTime.now()
        return oppgaveQueryRepository.queryForEksternId(oppgaveQuery, now)
    }

    fun queryToFile(oppgaveQuery: QueryRequest, idToken: IIdToken): String {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> queryToFile(tx, oppgaveQuery, idToken) }
        }
    }

    fun queryToFile(tx: TransactionalSession, oppgaveQuery: QueryRequest, idToken: IIdToken): String {
        val oppgaver = query(tx, oppgaveQuery, idToken)
        if (oppgaver.isEmpty()) {
            return ""
        }

        val oppgaverad = oppgaver[0]
        val oppgavefelter = oppgaveQueryRepository.hentAlleFelter().felter.associateBy {
            it.område + it.kode
        }

        val header = oppgaverad.felter.joinToString(";") { oppgavefelter[it.område + it.kode]?.visningsnavn ?: "" }

        return header + "\n" + oppgaver.joinToString("\n") { or: Oppgaverad ->
            or.felter.joinToString(";") {
                if (it.verdi == null) "" else it.verdi.toString()
            }
        }
    }

    fun query(oppgaveQuery: QueryRequest, idToken: IIdToken): List<Oppgaverad> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> query(tx, oppgaveQuery, idToken) }
        }
    }

    fun query(tx: TransactionalSession, request: QueryRequest, idToken: IIdToken): List<Oppgaverad> {
        val now = LocalDateTime.now()
        val oppgaveIder = oppgaveQueryRepository.query(tx, request, now)

        val oppgaverader = runBlocking(context = CoroutineRequestContext(idToken)) {
            mapAktiveOppgaver(tx, request, oppgaveIder, now)
        }

        if (request.oppgaveQuery.select.isEmpty()) {
            return listOf(Oppgaverad(listOf(Oppgavefeltverdi(null, "Antall", oppgaverader.size))))
        }

        return oppgaverader
    }

    fun hentAlleFelter(): Oppgavefelter {
        return oppgaveQueryRepository.hentAlleFelter()
    }

    private suspend fun mapAktiveOppgaver(tx: TransactionalSession, request: QueryRequest, oppgaveIder: List<AktivOppgaveId>, now: LocalDateTime): List<Oppgaverad> {
        val oppgaverader = mutableListOf<Oppgaverad>()
        val limit = request.avgrensning?.limit ?: -1
        var antall = 0
        for (oppgaveId in oppgaveIder) {
            val oppgaverad = mapAktivOppgave(tx, request.oppgaveQuery, oppgaveId, now)
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

    private suspend fun mapAktivOppgave(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, oppgaveId: AktivOppgaveId, now: LocalDateTime): Oppgaverad? {
        val oppgave = aktivOppgaveRepository.hentOppgaveForId(tx, oppgaveId, now)

        if (!pepClient.harTilgangTilOppgaveV3(oppgave = oppgave, action = Action.read, auditlogging = Auditlogging.LOGG_VED_PERMIT)) {
            return null
        }

        return if (oppgaveQuery.select.isEmpty()) {
            Oppgaverad(listOf())
        } else {
            val felter = toOppgavefeltverdier(oppgaveQuery, oppgave)
            Oppgaverad(felter)
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