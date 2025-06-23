package no.nav.k9.los.nyoppgavestyring.ko

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.DetaljerMetrikker
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.fnr
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.navn
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.NesteOppgaverFraKoDto
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.query.Avgrensning
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad
import no.nav.k9.los.nyoppgavestyring.reservasjon.AlleredeReservertException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.slf4j.LoggerFactory
import java.time.Duration
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
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
    private val reservasjonRepository: ReservasjonRepository,
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val statistikkChannel: Channel<Boolean>,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    private val antallOppgaverCache = AntallOppgaverForKøCache()
    private val antallOppgaverCacheVarighet = Duration.ofMinutes(5)

    @WithSpan
    fun hentOppgavekøer(skjermet: Boolean): List<OppgaveKo> {
        return oppgaveKoRepository.hentListe(skjermet)
    }

    @WithSpan
    suspend fun hentOppgaverFraKø(
        oppgaveKoId: Long,
        ønsketAntallSaker: Long,
        fjernReserverte: Boolean = false,
        idToken: IIdToken
    ): NesteOppgaverFraKoDto {
        val ko = oppgaveKoRepository.hent(oppgaveKoId, pepClient.harTilgangTilKode6())

        val selects = buildList {
            add(EnkelSelectFelt("K9", "aktorId"))
            add(EnkelSelectFelt("K9", "saksnummer"))
            add(EnkelSelectFelt("K9", "journalpostId"))
            add(EnkelSelectFelt("K9", "behandlingTypekode"))
            addAll(ko.oppgaveQuery.order.map {
                when (it) {
                    is EnkelOrderFelt -> EnkelSelectFelt(it.område, it.kode)
                }
            })
        }

        val køRespons = oppgaveQueryService.query(
            QueryRequest(
                oppgaveQuery = ko.oppgaveQuery.copy(select = selects),
                fjernReserverte = fjernReserverte,
                avgrensning = Avgrensning.maxAntall(ønsketAntallSaker)
            ),
            idToken
        )

        return byggDto(køRespons, ko.oppgaveQuery.order)
    }


    private suspend fun byggDto(
        køRespons: List<Oppgaverad>,
        orderFelter: List<OrderFelt>
    ): NesteOppgaverFraKoDto {
        val visningskolonner = buildMap {
            put("søker", "Søker")
            put("id", "Id")
            put("behandlingType", "Behandlingstype")
            putAll(orderFelter.map {
                val orderFelt = it as EnkelOrderFelt
                val visningsnavn =
                    feltdefinisjonTjeneste.hent(orderFelt.område!!).hentFeltdefinisjon(orderFelt.kode).visningsnavn
                orderFelt.kode to visningsnavn
            })
        }

        val rader: List<Map<String, String>> = køRespons.map { rad ->
            rad.mapNotNull { feltverdi ->
                feltverdi.verdi?.let { verdi ->
                    when (feltverdi.kode) {
                        "aktorId" -> "søker" to (pdlService.person(verdi as String).person
                            ?.let { "${it.navn()} ${it.fnr()}" }
                            ?: "Ukjent navn Ukjent fnummer")
                        "journalpostId", "saksnummer" -> "id" to (verdi as String)
                        "behandlingTypekode" -> "behandlingType" to BehandlingType.fraKode(verdi as String).navn
                        in visningskolonner -> feltverdi.kode to verdi.toString()
                        else -> null
                    }
                }
            }.toMap()
        }

        return NesteOppgaverFraKoDto(
            kolonner = visningskolonner,
            rader = rader
        )
    }

    @WithSpan
    fun hentKøerForSaksbehandler(
        saksbehandlerEpost: String,
        skjermet: Boolean
    ): List<OppgaveKo> {
        return transactionalManager.transaction { tx ->
            oppgaveKoRepository.hentKoerMedOppgittSaksbehandler(
                tx = tx,
                saksbehandlerEpost = saksbehandlerEpost,
                medSaksbehandlere = false,
                skjermet = skjermet
            )
        }
    }

    @WithSpan
    fun hentKøerForSaksbehandler(
        saksbehandlerId: Long,
        skjermet: Boolean
    ): List<OppgaveKo> {
        return transactionalManager.transaction { tx ->
            oppgaveKoRepository.hentKoerMedOppgittSaksbehandler(
                tx = tx,
                saksbehandlerId = saksbehandlerId,
                medSaksbehandlere = false,
                skjermet = skjermet
            )
        }
    }

    @WithSpan
    suspend fun hentAntallMedOgUtenReserverteForKø(
        oppgaveKoId: Long,
        skjermet: Boolean,
    ): AntallOppgaverOgReserverte {
        return coroutineScope {
            val antallUtenReserverte = async(Dispatchers.IO + Span.current().asContextElement()) {
                hentAntallOppgaverForKø(oppgaveKoId = oppgaveKoId, filtrerReserverte = true, skjermet = skjermet)
            }
            val antallMedReserverte = async(Dispatchers.IO + Span.current().asContextElement()) {
                hentAntallOppgaverForKø(oppgaveKoId = oppgaveKoId, filtrerReserverte = false, skjermet = skjermet)
            }

            AntallOppgaverOgReserverte(
                antallUtenReserverte.await(),
                antallMedReserverte.await()
            )
        }
    }

    @WithSpan
    fun hentAntallOppgaverForKø(
        oppgaveKoId: Long,
        filtrerReserverte: Boolean,
        skjermet: Boolean
    ): Long {
        val ko = oppgaveKoRepository.hent(oppgaveKoId, skjermet)
        return antallOppgaverCache.hent(
            AntallOppgaverForKøCacheKey(oppgaveKoId, filtrerReserverte),
            antallOppgaverCacheVarighet
        )
        { oppgaveQueryService.queryForAntall(QueryRequest(ko.oppgaveQuery, fjernReserverte = filtrerReserverte)) }
    }

    @WithSpan
    fun taReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
        coroutineContext: CoroutineContext
    ): OppgaveMuligReservert {
        return DetaljerMetrikker.time("taReservasjonFraKø", "hele", "$oppgaveKoId") {
            doTaReservasjonFraKø(innloggetBrukerId, oppgaveKoId, coroutineContext)
                .also {
                    when (it) {
                        is OppgaveMuligReservert.Reservert ->
                            // oppdater cache ved å redusere antall dersom reservasjon ble tatt
                            antallOppgaverCache.decrementValue(
                                AntallOppgaverForKøCacheKey(
                                    oppgaveKoId,
                                    filtrerReserverte = true
                                )
                            )

                        OppgaveMuligReservert.IkkeReservert -> {
                            // ikke oppdater cache siden ingenting er endret
                        }
                    }
                }
        }
    }

    private fun doTaReservasjonFraKø(
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
        coroutineContext: CoroutineContext
    ): OppgaveMuligReservert {
        log.info("taReservasjonFraKø, oppgaveKøId: $oppgaveKoId")
        val skjermet = runBlocking(Dispatchers.IO + coroutineContext) { pepClient.harTilgangTilKode6() }
        val oppgavekø = DetaljerMetrikker.time("taReservasjonFraKø", "hentKø", "$oppgaveKoId") {
            oppgaveKoRepository.hent(
                oppgaveKoId,
                skjermet
            )
        }

        var antallKandidaterEtterspurt = 1
        while (true) {
            val kandidatOppgaver = DetaljerMetrikker.time("taReservasjonFraKø", "queryForOppgaveId", "$oppgaveKoId") {
                    oppgaveQueryService.queryForOppgave(
                        QueryRequest(
                            oppgavekø.oppgaveQuery,
                            fjernReserverte = true,
                            avgrensning = Avgrensning(limit = antallKandidaterEtterspurt.toLong())
                        )
                    )
                }
            log.info("Spurte etter $antallKandidaterEtterspurt kandidater fra køen med id $oppgaveKoId, fikk ${kandidatOppgaver.size}")
            val muligReservert = DetaljerMetrikker.time("taReservasjonFraKø", "finnReservasjonFraKø", "$oppgaveKoId") {
                    transactionalManager.transaction { tx ->
                        finnReservasjonFraKø(kandidatOppgaver, tx, innloggetBrukerId, coroutineContext)
                    }
                }
            if (muligReservert is OppgaveMuligReservert.Reservert) {
                return muligReservert
            }
            if (kandidatOppgaver.size < antallKandidaterEtterspurt) {
                //vi hentet alle oppgavene i køen, ikke vits å prøve mer
                return OppgaveMuligReservert.IkkeReservert
            }
            log.info("Hadde ${kandidatOppgaver.size} uten å klare å ta reservasjon, forsøker igjen med flere kandidater")
            antallKandidaterEtterspurt *= 2
        }
    }

    @WithSpan
    private fun finnReservasjonFraKø(
        kandidatoppgaver: List<Oppgave>,
        tx: TransactionalSession,
        innloggetBrukerId: Long,
        coroutineContext: CoroutineContext,
    ): OppgaveMuligReservert {
        for (kandidatoppgave in kandidatoppgaver) {
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

                runBlocking(Dispatchers.IO + coroutineContext) {//TODO: Hvis noen har et forslag til en bedre måte å ta vare på coroutinecontext, så er jeg all ears!
                    saksbehandlerRepository.leggTilFlereReservasjoner(
                        innloggetBruker.brukerIdent,
                        v1Reservasjoner.map { r -> r.oppgave })
                    statistikkChannel.send(true)
                }
                // V1-greier til og med denne linjen
                val reservasjon = reservasjonV3Tjeneste.taReservasjonMenSjekkLegacyFørst(
                    reserverForId = innloggetBrukerId,
                    utføresAvId = innloggetBrukerId,
                    reservasjonsnøkkel = kandidatoppgave.reservasjonsnøkkel,
                    gyldigFra = LocalDateTime.now(),
                    gyldigTil = LocalDateTime.now().leggTilDagerHoppOverHelg(2),
                    kommentar = "",
                    tx = tx
                )
                return OppgaveMuligReservert.Reservert(kandidatoppgave, reservasjon)
            } catch (e: AlleredeReservertException) {
                log.warn("2 saksbehandlere prøvde å reservere nøkkel samtidig, reservasjonsnøkkel: ${kandidatoppgave.reservasjonsnøkkel}")
                continue //TODO: Ved mange brukere her trenger vi kanskje en eller annen form for backoff, så ikke alle går samtidig på neste kandidat
            } catch (e: ManglerTilgangException) {
                log.info(e.message)
                continue
            }
        }
        return OppgaveMuligReservert.IkkeReservert
    }

    @WithSpan
    suspend fun hentSaksbehandlereForKo(oppgaveKoId: Long): List<Saksbehandler> {
        val oppgaveKo = oppgaveKoRepository.hent(oppgaveKoId, pepClient.harTilgangTilKode6())
        return oppgaveKo.saksbehandlere.mapNotNull { saksbehandlerEpost: String ->
            saksbehandlerRepository.finnSaksbehandlerMedEpost(saksbehandlerEpost).also {
                if (it == null) {
                    log.info("Køen $oppgaveKoId inneholder saksbehandler som ikke finnes")
                }
            }
        }
    }

    @WithSpan
    fun kopier(
        kopierFraOppgaveId: Long,
        tittel: String,
        taMedQuery: Boolean,
        taMedSaksbehandlere: Boolean,
        skjermet: Boolean
    ): OppgaveKo {
        val kø = oppgaveKoRepository.kopier(kopierFraOppgaveId, tittel, taMedQuery, taMedSaksbehandlere, skjermet)
        runBlocking {
            køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        }
        return kø
    }

    @WithSpan
    fun leggTil(tittel: String, skjermet: Boolean): OppgaveKo {
        val kø = oppgaveKoRepository.leggTil(tittel, skjermet)
        runBlocking {
            køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        }
        return kø
    }

    @WithSpan
    fun hent(oppgaveKoId: Long, harTilgangTilKode6: Boolean): OppgaveKo {
        return oppgaveKoRepository.hent(oppgaveKoId, harTilgangTilKode6)
    }

    @WithSpan
    fun slett(oppgaveKoId: Long) {
        oppgaveKoRepository.slett(oppgaveKoId)
        runBlocking {
            køpåvirkendeHendelseChannel.send(KødefinisjonSlettet(oppgaveKoId))
        }
        antallOppgaverCache.slettForKøId(oppgaveKoId)
    }

    fun endre(oppgaveKo: OppgaveKo, skjermet: Boolean): OppgaveKo {
        val kø = oppgaveKoRepository.endre(oppgaveKo, skjermet)
        runBlocking {
            køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        }
        antallOppgaverCache.slettForKøId(kø.id)
        return kø
    }

    fun clearCache() {
        antallOppgaverCache.clear()
    }

    data class AntallOppgaverForKøCacheKey(val oppgaveKoId: Long, val filtrerReserverte: Boolean)

    class AntallOppgaverForKøCache : Cache<AntallOppgaverForKøCacheKey, Long>(cacheSizeLimit = null) {

        fun slettForKøId(køId: Long) {
            withWriteLock {
                remove(AntallOppgaverForKøCacheKey(køId, false))
                remove(AntallOppgaverForKøCacheKey(køId, true))
            }
        }

        fun decrementValue(nøkkel: AntallOppgaverForKøCacheKey) {
            withWriteLock {
                val cacheObject = keyValueMap[nøkkel]
                if (cacheObject != null && cacheObject.value > 0) {
                    keyValueMap[nøkkel] = cacheObject.copy(value = cacheObject.value - 1)
                }
            }
        }


    }
}