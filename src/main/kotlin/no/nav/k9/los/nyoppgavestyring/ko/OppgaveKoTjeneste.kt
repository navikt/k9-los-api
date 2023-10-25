package no.nav.k9.los.nyoppgavestyring.ko

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.PepClient
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
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
    private val oppgaveRepositoryTxWrapper: OppgaveRepositoryTxWrapper,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val reservasjonRepository: ReservasjonRepository,
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    fun hentOppgaverFraKø(
        oppgaveKoId: Long,
        ønsketAntallSaker: Int,
    ): List<GenerellOppgaveV3Dto> {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)

        val oppgaveEksternIder = oppgaveQueryService.queryForOppgaveEksternId(ko.oppgaveQuery)
        //TODO Filtrere bort allerede reserverte oppgaver
        var antallOppgaverFunnet = 0
        return oppgaveEksternIder.takeWhile { antallOppgaverFunnet < ønsketAntallSaker }.mapNotNull { oppgaveEksternId ->
            val oppgave = oppgaveRepositoryTxWrapper.hentOppgave(oppgaveEksternId)
            val person = runBlocking {
                pdlService.person(oppgave.hentVerdi("aktorId")!!)
            }.person!!
            val harTilgangTilLesSak = runBlocking { //TODO: harTilgangTilLesSak riktig PEP-spørring, eller bær det være likhetssjekk?
                pepClient.harTilgangTilLesSak(oppgave.hentVerdi("saksnummer")!!, oppgave.hentVerdi("aktorId")!!)
            }
            if (harTilgangTilLesSak) {
                antallOppgaverFunnet++
                GenerellOppgaveV3Dto(oppgave, person)
            } else {
                null
            }
        }
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
                //if (kandidatoppgave.oppgavetype.eksternId == "k9klage") //TODO: Hvis klageoppgave/klagekø -- IKKE ta reservasjon i V1. Disse kan ikke speiles
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