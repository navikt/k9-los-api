package no.nav.k9.los.nyoppgavestyring.query

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.koin.java.KoinJavaComponent.inject
import java.lang.RuntimeException
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
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

    fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuery, ctx: CoroutineContext): List<Oppgaverad> {
        val oppgaver: List<Long> = oppgaveQueryRepository.query(tx, oppgaveQuery)

        val oppgaverader = runBlocking(context = ctx) {
            oppgaver.mapNotNull {
                val oppgave = oppgaveRepository.hentOppgaveForId(tx, it)

                // TODO: Generaliser ABAC-attributter + sjekk av disse:
                val saksnummer = oppgave.hentVerdi("K9", "saksnummer")
                val aktorId = oppgave.hentVerdi("K9", "aktorId")!!

                if (saksnummer === null || !pepClient.harTilgangTilLesSak(saksnummer, aktorId)) {
                    null
                } else if (oppgaveQuery.select.isEmpty()) {
                    Oppgaverad(listOf())
                } else {
                    val felter = toOppgavefeltverdier(oppgaveQuery, oppgave)
                    Oppgaverad(felter)
                }
            }
        }

        if (oppgaveQuery.select.isEmpty()) {
            return listOf(Oppgaverad(listOf(Oppgavefeltverdi(null, "Antall", oppgaverader.size))))
        }

        return oppgaverader
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

    suspend fun query(oppgaveQuery: OppgaveQuery): List<Oppgaverad> {
        val ctx = coroutineContext
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> query(tx, oppgaveQuery, ctx) }
        }
    }
}