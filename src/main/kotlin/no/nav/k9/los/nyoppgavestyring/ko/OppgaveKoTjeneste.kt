package no.nav.k9.los.nyoppgavestyring.ko

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.reservasjon.*
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDato
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext

class OppgaveKoTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveRepositoryTxWrapper: OppgaveRepositoryTxWrapper,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val reservasjonV3Repository: ReservasjonV3Repository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val reservasjonRepository: ReservasjonRepository,
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    fun hentOppgavekøerMedAntall(): Map<OppgaveKo, Long> {
        return oppgaveKoRepository.hentListe()
            .associateWith {
                oppgaveQueryService.queryForAntall(it.oppgaveQuery)
            }
    }

    suspend fun hentOppgaverFraKø(
        oppgaveKoId: Long,
        ønsketAntallSaker: Int,
    ): List<GenerellOppgaveV3Dto> {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)

        val køoppgaveIder = oppgaveQueryService.queryForOppgaveEksternId(ko.oppgaveQuery)
        var oppgaver = mutableListOf<GenerellOppgaveV3Dto>()
        for (eksternOppgaveId in køoppgaveIder) {
            val oppgave = oppgaveRepositoryTxWrapper.hentOppgave(eksternOppgaveId.område, eksternOppgaveId.eksternId)

            val aktivReservasjon =
                reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)
            if (aktivReservasjon != null) {
                continue
            }

            val harTilgangTilLesSak = //TODO: harTilgangTilLesSak riktig PEP-spørring, eller bør det være likhetssjekk?
                pepClient.harTilgangTilLesSak(oppgave.hentVerdi("saksnummer")!!, oppgave.hentVerdi("aktorId")!!)
            if (!harTilgangTilLesSak) {
                continue
            }

            val person = pdlService.person(oppgave.hentVerdi("aktorId")!!).person!!

            oppgaver.add(GenerellOppgaveV3Dto(oppgave, person))
            if (oppgaver.size >= ønsketAntallSaker) {
                break
            }
        }
        return oppgaver.toList()
    }

    fun hentKøerForSaksbehandler(
        saksbehandlerEpost: String
    ): List<OppgaveKo> {
        return transactionalManager.transaction { tx ->
            oppgaveKoRepository.hentKoerMedOppgittSaksbehandler(tx, saksbehandlerEpost)
        }
    }

    fun hentAntallUreserverteOppgaveForKø(
        oppgaveKoId: Long
    ): Long {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)
        val oppgaveIder = oppgaveQueryService.queryForOppgaveId(ko.oppgaveQuery)
        val ureserverte = reservasjonV3Repository.hentUreserverteOppgaveIder(oppgaveIder)
        return ureserverte.size.toLong()
    }

    fun taReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
        coroutineContext: CoroutineContext
    ): Pair<Oppgave, ReservasjonV3>? {
        val oppgavekø = oppgaveKoRepository.hent(oppgaveKoId)

        val kandidatOppgaver = oppgaveQueryService.queryForOppgaveId(oppgavekø.oppgaveQuery)

        return transactionalManager.transaction { tx ->
            finnReservasjonFraKø(kandidatOppgaver, tx, innloggetBrukerId, coroutineContext)
        }
    }

    private fun finnReservasjonFraKø(
        kandidatoppgaver: List<Long>,
        tx: TransactionalSession,
        innloggetBrukerId: Long,
        coroutineContext: CoroutineContext,
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

                runBlocking(coroutineContext) {//TODO: Hvis noen har et forslag til en bedre måte å ta vare på coroutinecontext, så er jeg all ears!
                    saksbehandlerRepository.leggTilFlereReservasjoner(
                        innloggetBruker.brukerIdent,
                        v1Reservasjoner.map { r -> r.oppgave })
                }
                // V1-greier til og med denne linjen
                val reservasjon = reservasjonV3Tjeneste.taReservasjon(
                    reserverForId = innloggetBrukerId,
                    utføresAvId = innloggetBrukerId,
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

    suspend fun hentSaksbehandlereForKo(oppgaveKoId: Long): List<Saksbehandler> {
        val oppgaveKo = oppgaveKoRepository.hent(oppgaveKoId)
        return oppgaveKo.saksbehandlere.mapNotNull { saksbehandlerEpost: String ->
            saksbehandlerRepository.finnSaksbehandlerMedEpost(saksbehandlerEpost).also {
                if (it == null) {
                    log.info("Køen $oppgaveKoId inneholder saksbehandler som ikke finnes")
                }
            }
        }
    }
}