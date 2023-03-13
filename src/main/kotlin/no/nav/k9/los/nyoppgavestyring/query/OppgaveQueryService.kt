package no.nav.k9.los.nyoppgavestyring.query

import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.CoroutineRequestContext
import no.nav.k9.los.integrasjon.rest.idToken
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
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class OppgaveQueryService() {
    val datasource by inject<DataSource>(DataSource::class.java)
    val oppgaveQueryRepository by inject<OppgaveQueryRepository>(OppgaveQueryRepository::class.java)
    val oppgaveRepository by inject<OppgaveRepository>(OppgaveRepository::class.java)
    val pepClient by inject<IPepClient>(IPepClient::class.java)

    fun hentAlleFelter(): Oppgavefelter {
        return oppgaveQueryRepository.hentAlleFelter()
    }

    fun queryForOppgaveId(oppgaveQuery: OppgaveQuery): List<Long> {
        return oppgaveQueryRepository.query(oppgaveQuery)
    }

    fun queryToFile(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, idToken: IIdToken): String {
        val oppgaver = query(tx, oppgaveQuery, idToken)
        if (oppgaver.isEmpty()) {
            return ""
        }

        val oppgaverad = oppgaver[0]
        val oppgavefelter = oppgaveQueryRepository.hentAlleFelter().felter.associateBy {
            it.område + it.kode
        }

        val header = oppgaverad.felter.joinToString(";") { oppgavefelter[it.område + it.kode]?.visningsnavn?:"" }

        return header + "\n" + oppgaver.joinToString("\n") { or: Oppgaverad ->
            or.felter.joinToString(";") {
                if (it.verdi == null) "" else it.verdi.toString()
            }
        }
    }

    fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, idToken: IIdToken): List<Oppgaverad> {
        val oppgaver: List<Long> = oppgaveQueryRepository.query(tx, oppgaveQuery)

        val oppgaverader = runBlocking(context = CoroutineRequestContext(idToken)) {
            mapOppgaver(tx, oppgaveQuery, oppgaver)
        }

        if (oppgaveQuery.select.isEmpty()) {
            return listOf(Oppgaverad(listOf(Oppgavefeltverdi(null, "Antall", oppgaverader.size))))
        }

        return oppgaverader
    }

    private suspend fun mapOppgaver(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, oppgaveIder: List<Long>): List<Oppgaverad> {
        val oppgaverader = mutableListOf<Oppgaverad>()
        val limit = oppgaveQuery.limit
        var antall = 0
        for (oppgaveId in oppgaveIder) {
            val oppgaverad = mapOppgave(tx, oppgaveQuery, oppgaveId)
            if (oppgaverad != null) {
                oppgaverader.add(oppgaverad)
                antall++
                if (limit >= 0 && antall >= limit) {
                    return oppgaverader
                }
            }
        }
        return oppgaverader
    }

    private suspend fun mapOppgave(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, oppgaveId: Long): Oppgaverad? {
        val oppgave = oppgaveRepository.hentOppgaveForId(tx, oppgaveId)

        // TODO: Generaliser ABAC-attributter + sjekk av disse:
        val saksnummer = oppgave.hentVerdi("K9", "saksnummer")
        val aktorId = oppgave.hentVerdi("K9", "aktorId")!!

        if (saksnummer === null || !pepClient.harTilgangTilLesSak(saksnummer, aktorId)) {
            return null
        } else if (oppgaveQuery.select.isEmpty()) {
            return Oppgaverad(listOf())
        } else {
            val felter = toOppgavefeltverdier(oppgaveQuery, oppgave)
            return Oppgaverad(felter)
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
                "oppgavetype" -> throw RuntimeException("Not implemented yet.")
                "oppgaveområde" -> throw RuntimeException("Not implemented yet.")
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

    fun query(oppgaveQuery: OppgaveQuery, idToken: IIdToken): List<Oppgaverad> {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> query(tx, oppgaveQuery, idToken) }
        }
    }

    fun queryToFile(oppgaveQuery: OppgaveQuery, idToken: IIdToken): String {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> queryToFile(tx, oppgaveQuery, idToken) }
        }
    }
}