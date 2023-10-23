package no.nav.k9.los.nyoppgavestyring.ko

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad
import no.nav.k9.los.nyoppgavestyring.reservasjon.AlleredeReservertException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDato
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class OppgaveKoTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val reservasjonRepository: ReservasjonRepository,
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    fun hentOppgaverFraKø(
        oppgaveKoId: Long,
        antallSaker: Int,
    ): List<Oppgaverad> {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)
        val idToken = runBlocking {
            kotlin.coroutines.coroutineContext.idToken()
        }

        /*
        // WIP
        //TODO: Paging bør flyttes til queryservice?
        val oppgaveEksternIder = oppgaveQueryService.queryForOppgaveEksternId(ko.oppgaveQuery)

        var oppgaveDtoer: List<GenerellOppgaveV3Dto>
        //while listeForKort
            //hent page
            //iterer og fyll liste
        while ()
        oppgaveRepositoryTxWrapper.hentOppgaverPaget(oppgaveEksternIder, antallSaker, i)
        //oppgaveRepositoryTxWrapper.hentOppgaver(oppgaveIder, true, antallSaker)
         */

        return oppgaveQueryService.query(ko.oppgaveQuery.copy(limit = antallSaker), idToken)
    }

    fun hentKøerForSaksbehandler(
        saksbehandlerEpost: String
    ): List<OppgaveKo> {
        return transactionalManager.transaction { tx ->
            oppgaveKoRepository.hentKoerMedOppgittSaksbehandler(tx, saksbehandlerEpost)
        }
    }

    fun hentAntallOppgaveForKø(
        oppgaveKoId: Long
    ): Long {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)
        return oppgaveQueryService.queryForAntall(ko.oppgaveQuery)
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
                // Fjernes når V1 skal vekk
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedId(innloggetBrukerId)
                val åpneOppgaverForReservasjonsnøkkel =
                    oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, kandidatoppgave.reservasjonsnøkkel)
                val v1Reservasjoner = oppgaveTjeneste.lagReservasjoner(
                    åpneOppgaverForReservasjonsnøkkel.map { UUID.fromString(it.eksternId) }.toSet(),
                    innloggetBruker.brukerIdent!!,
                    null,
                )
                reservasjonRepository.lagreFlereReservasjoner(v1Reservasjoner)
                runBlocking {
                    saksbehandlerRepository.leggTilFlereReservasjoner(innloggetBruker.brukerIdent, v1Reservasjoner.map { r -> r.oppgave })
                }
                // V1-greier til og med denne linjen
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

    fun hentSaksbehandlereForKo(oppgaveKoId: Long): List<Saksbehandler> {
        val oppgaveKo = oppgaveKoRepository.hent(oppgaveKoId)
        return oppgaveKo.saksbehandlere.map { saksbehandlerEpost: String ->
            runBlocking {
                saksbehandlerRepository.finnSaksbehandlerMedEpost(saksbehandlerEpost)
            }!!
        }
    }
}