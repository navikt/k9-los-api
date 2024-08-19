package no.nav.k9.los.nyoppgavestyring.ko

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.eventhandler.DetaljerMetrikker
import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.Auditlogging
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.query.Avgrensning
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.reservasjon.*
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.GenerellOppgaveV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.utils.leggTilDagerHoppOverHelg
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext

class OppgaveKoTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val oppgaveRepository: OppgaveRepository,
    private val aktivOppgaveRepository: AktivOppgaveRepository,
    private val oppgaveRepositoryTxWrapper: OppgaveRepositoryTxWrapper,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val reservasjonRepository: ReservasjonRepository,
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val statistikkChannel: Channel<Boolean>,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    fun hentOppgavekøer(skjermet: Boolean = false): List<OppgaveKo> {
        return oppgaveKoRepository.hentListe(skjermet)
    }

    suspend fun hentOppgaverFraKø(
        oppgaveKoId: Long,
        ønsketAntallSaker: Long,
        fjernReserverte: Boolean = false
    ): List<GenerellOppgaveV3Dto> {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)

        val køoppgaveIder = oppgaveQueryService.queryForOppgaveEksternId(QueryRequest(ko.oppgaveQuery, fjernReserverte = fjernReserverte, Avgrensning.maxAntall(ønsketAntallSaker)))
        val oppgaver = mutableListOf<GenerellOppgaveV3Dto>()
        for (eksternOppgaveId in køoppgaveIder) {
            val oppgave = oppgaveRepositoryTxWrapper.hentOppgave(eksternOppgaveId.område, eksternOppgaveId.eksternId)

            if (!pepClient.harTilgangTilOppgaveV3(oppgave, Action.read, Auditlogging.LOGG_VED_PERMIT)) {
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

    fun hentAntallOppgaverForKø(
        oppgaveKoId: Long,
        filtrerReserverte: Boolean
    ): Long {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)
        return oppgaveQueryService.queryForAntall(QueryRequest(ko.oppgaveQuery, fjernReserverte = filtrerReserverte))
    }

    fun hentAntallUreserverteOppgaveForKø(
        oppgaveKoId: Long
    ): Long {
        val ko = oppgaveKoRepository.hent(oppgaveKoId)
        return oppgaveQueryService.queryForAntall(QueryRequest(ko.oppgaveQuery, fjernReserverte = true))
    }

    fun taReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
        coroutineContext: CoroutineContext
    ): Pair<Oppgave, ReservasjonV3>? {
        return DetaljerMetrikker.time("taReservasjonFraKø", "hele", "$oppgaveKoId" ) {
            doTaReservasjonFraKø(innloggetBrukerId, oppgaveKoId, coroutineContext)
        }
    }

    fun doTaReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
        coroutineContext: CoroutineContext
    ): Pair<Oppgave, ReservasjonV3>? {
        log.info("taReservasjonFraKø, oppgaveKøId: $oppgaveKoId")

        val oppgavekø = DetaljerMetrikker.time("taReservasjonFraKø", "hentKø", "$oppgaveKoId" ) { oppgaveKoRepository.hent(oppgaveKoId) }

        var antallKandidaterEtterspurt = 1
        while (true) {
            val kandidatOppgaver = DetaljerMetrikker.time("taReservasjonFraKø", "queryForOppgaveId","$oppgaveKoId" ) {
                oppgaveQueryService.queryForOppgaveId(
                    QueryRequest(
                        oppgavekø.oppgaveQuery,
                        fjernReserverte = true,
                        avgrensning = Avgrensning(limit = antallKandidaterEtterspurt.toLong())
                    )
                )
            }
            log.info("Spurte etter $antallKandidaterEtterspurt kandidater fra køen med id $oppgaveKoId, fikk ${kandidatOppgaver.size}")
            val reservasjon =  DetaljerMetrikker.time("taReservasjonFraKø", "finnReservasjonFraKø","$oppgaveKoId" ) {
                transactionalManager.transaction { tx ->
                    finnReservasjonFraKø(kandidatOppgaver, tx, innloggetBrukerId, coroutineContext)
                }
            }
            if (reservasjon != null){
                return reservasjon
            }
            if (kandidatOppgaver.size < antallKandidaterEtterspurt) {
                //vi hentet alle oppgavene i køen, ikke vits å prøve mer
                return null
            }
            log.info("Hadde ${kandidatOppgaver.size} uten å klare å ta reservasjon, forsøker igjen med flere kandidater")
            antallKandidaterEtterspurt *= 2
        }
    }

    private fun finnReservasjonFraKø(
        kandidatoppgaver: List<AktivOppgaveId>,
        tx: TransactionalSession,
        innloggetBrukerId: Long,
        coroutineContext: CoroutineContext,
    ): Pair<Oppgave, ReservasjonV3>? {
        for (kandidatoppgaveId in kandidatoppgaver) {
            val kandidatoppgave = aktivOppgaveRepository.hentOppgaveForId(tx, kandidatoppgaveId)

            try {
                //if (kandidatoppgave.oppgavetype.eksternId == "k9klage") //TODO: Hvis klageoppgave/klagekø -- IKKE ta reservasjon i V1. Disse kan ikke speiles
                // Fjernes når V1 skal vekk
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedId(innloggetBrukerId)!!
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
                    statistikkChannel.send(true)
                }
                // V1-greier til og med denne linjen
                val reservasjon = reservasjonV3Tjeneste.taReservasjon(
                    reserverForId = innloggetBrukerId,
                    utføresAvId = innloggetBrukerId,
                    reservasjonsnøkkel = kandidatoppgave.reservasjonsnøkkel,
                    gyldigFra = LocalDateTime.now(),
                    gyldigTil = LocalDateTime.now().leggTilDagerHoppOverHelg(2),
                    kommentar = "",
                    tx = tx
                )
                return Pair(kandidatoppgave, reservasjon)
            } catch (e: AlleredeReservertException) {
                log.warn("2 saksbehandlere prøvde å reservere nøkkel samtidig, reservasjonsnøkkel: ${kandidatoppgave.reservasjonsnøkkel}")
                continue //TODO: Ved mange brukere her trenger vi kanskje en eller annen form for backoff, så ikke alle går samtidig på neste kandidat
            } catch (e: ManglerTilgangException) {
                log.info(e.message)
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

    fun kopier(kopierFraOppgaveId: Long, tittel: String, taMedQuery: Boolean, taMedSaksbehandlere: Boolean): OppgaveKo {
        val kø = oppgaveKoRepository.kopier(kopierFraOppgaveId, tittel, taMedQuery, taMedSaksbehandlere)
        runBlocking {
            køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        }
        return kø
    }

    fun leggTil(tittel: String, skjermet: Boolean): OppgaveKo {
        val kø = oppgaveKoRepository.leggTil(tittel, skjermet)
        runBlocking {
            køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        }
        return kø
    }

    fun hent(oppgaveKoId: Long): OppgaveKo {
        return oppgaveKoRepository.hent(oppgaveKoId)
    }

    fun slett(oppgaveKoId: Long) {
        oppgaveKoRepository.slett(oppgaveKoId)
        runBlocking {
            køpåvirkendeHendelseChannel.send(KødefinisjonSlettet(oppgaveKoId))
        }
    }

    fun endre(oppgaveKo: OppgaveKo): OppgaveKo {
        val kø = oppgaveKoRepository.endre(oppgaveKo)
        runBlocking {
            køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        }
        return kø
    }

}