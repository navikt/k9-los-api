package no.nav.k9.los.nyoppgavestyring.ko

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad
import no.nav.k9.los.nyoppgavestyring.reservasjon.AlleredeReservertException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDato
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class OppgaveKoTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    fun hentOppgaverFraKø(
        oppgaveKoId: Long
    ) : List<Oppgaverad> {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)
        val idToken = runBlocking {
            kotlin.coroutines.coroutineContext.idToken()
        }
        return oppgaveQueryService.query(ko.oppgaveQuery, idToken)
    }

    fun taReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long
    ): Pair<Oppgave, ReservasjonV3>? {
        val oppgavekø = transactionalManager.transaction { tx ->
            oppgaveKoRepository.hent(oppgaveKoId)
        }

        val kandidatOppgaver = oppgaveQueryService.queryForOppgaveId(oppgavekø.oppgaveQuery)

        return transactionalManager.transaction { tx ->
            finnReservasjonFraKø(kandidatOppgaver, tx, innloggetBrukerId)
        }

    }

    private fun finnReservasjonFraKø(
        kandidatoppgaver: List<Long>,
        tx: TransactionalSession,
        innloggetBrukerId: Long
    ): Pair<Oppgave, ReservasjonV3>? {
        for (kandidatoppgaveId in kandidatoppgaver) {
            val kandidatoppgave = oppgaveRepository.hentOppgaveForId(tx, kandidatoppgaveId)
            //reservert allerede?
            val aktivReservasjon =
                reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(
                    kandidatoppgave.reservasjonsnøkkel,
                    tx
                )
            if (aktivReservasjon != null) {
                continue
            }

            try {
                val reservasjon = reservasjonV3Tjeneste.taReservasjon(
                    reserverForId = innloggetBrukerId,
                    reservasjonsnøkkel = kandidatoppgave.reservasjonsnøkkel,
                    gyldigFra = LocalDateTime.now(),
                    gyldigTil = LocalDateTime.now().plusHours(24).forskyvReservasjonsDato(),
                    kommentar = "",
                    tx = tx
                )
                return Pair(kandidatoppgave, reservasjon)
            } catch (e: AlleredeReservertException) {
                log.warn("2 saksbehandlere prøvde å reservere nøkkel samtidig, reservasjonsnøkkel: ${kandidatoppgave.reservasjonsnøkkel}")
                continue //TODO: Ved mange brukere her trenger vi kanskje en eller annen form for backoff, så ikke alle går samtidig på neste kandidat
            } catch (e: ManglerTilgangException) {
                log.info("Saksbehandler $innloggetBrukerId mangler tilgang til å reservere nøkkel ${kandidatoppgave.reservasjonsnøkkel}")
                continue
            }
        }
        return null
    }
}