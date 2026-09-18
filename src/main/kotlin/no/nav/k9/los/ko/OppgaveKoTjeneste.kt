package no.nav.k9.los.ko

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotliquery.TransactionalSession
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.metrikker.DetaljerMetrikker
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.fnr
import no.nav.k9.los.infrastruktur.pdl.navn
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.leggTilDagerHoppOverHelg
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.ko.dto.NesteOppgaverFraKoDto
import no.nav.k9.los.ko.dto.OppgaveKo
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.Avgrensning
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.reservasjon.AlleredeReservertException
import no.nav.k9.los.reservasjon.ManglerTilgangException
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

class OppgaveKoTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKoRepository: OppgaveKoRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pdlService: IPdlService,
    private val pepClient: IPepClient,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgaveSammendragDtoBuilder: OppgaveSammendragDtoBuilder,
) {
    private val log = LoggerFactory.getLogger(OppgaveKoTjeneste::class.java)

    private val antallOppgaverCache = AntallOppgaverForKøCache()
    private val antallOppgaverCacheVarighet = Duration.ofMinutes(5)

    @WithSpan
    fun hentOppgavekøer(område: Områder, skjermet: Boolean): List<OppgaveKo> {
        return oppgaveKoRepository.hentListe(område, skjermet)
    }

    @WithSpan
    suspend fun hentOppgaverFraKø(
        område: Områder,
        idToken: IIdToken,
        oppgaveKoId: Long,
        ønsketAntallOppgaver: Long,
        fjernReserverte: Boolean = false
    ): NesteOppgaverFraKoDto {
        val kø = oppgaveKoRepository.hent(område, pepClient.harTilgangTilKode6(), oppgaveKoId)
        val tilgjengeligeOppgaver = hentTilgjengeligeOppgaverFraKø(
            område = område,
            idToken = idToken,
            kø = kø,
            ønsketAntallOppgaver = ønsketAntallOppgaver,
            fjernReserverte = fjernReserverte,
        )

        // Kun mulig med en enkelt order på køer per i dag. Inkluderer den som kolonne.
        val orderFelt = kø.oppgaveQuery.order.filterIsInstance<EnkelOrderFelt>().firstOrNull()
        return byggDto(tilgjengeligeOppgaver, orderFelt)
    }

    @WithSpan
    suspend fun hentOppgaverFraKøSammendrag(
        område: Områder,
        kode6: Boolean,
        idToken: IIdToken,
        oppgaveKoId: Long,
        ønsketAntallOppgaver: Long,
        fjernReserverte: Boolean = false,
    ): List<OppgaveSammendragDto> {
        val kø = oppgaveKoRepository.hent(område, kode6, oppgaveKoId)
        val oppgaver = hentTilgjengeligeOppgaverFraKø(område, idToken, kø, ønsketAntallOppgaver, fjernReserverte)
        return oppgaveSammendragDtoBuilder.bygg(oppgaver)
    }

    private suspend fun hentTilgjengeligeOppgaverFraKø(
        område: Områder,
        idToken: IIdToken,
        kø: OppgaveKo,
        ønsketAntallOppgaver: Long,
        fjernReserverte: Boolean,
    ): List<Oppgave> {
        val kandidatOppgaver = oppgaveQueryService.queryForOppgave(
            QueryRequest(
                område = område,
                oppgaveQuery = kø.oppgaveQuery,
                fjernReserverte = fjernReserverte,
                avgrensning = Avgrensning.maxAntall(ønsketAntallOppgaver),
            )
        )

        val tilgjengeligeOppgaver = kandidatOppgaver.filter { pepClient.harTilgangTilOppgaveV3(område, idToken, it) }
        val filtrertBort = kandidatOppgaver.size - tilgjengeligeOppgaver.size
        if (filtrertBort > 0) {
            log.info("Filtrerte bort {} oppgaver fra kø {} etter pepClient-kall", filtrertBort, kø.id)
        }

        return tilgjengeligeOppgaver
    }


    private suspend fun byggDto(
        oppgaver: List<Oppgave>,
        orderFelt: EnkelOrderFelt?
    ): NesteOppgaverFraKoDto {

        val visningskolonner = buildMap {
            put("søker", "Søker")
            put("id", "Id")
            put("behandlingType", "Behandlingstype")
            if (orderFelt != null) {
                val orderVisningsnavn =
                    feltdefinisjonTjeneste.hent(orderFelt.område!!).hentFeltdefinisjon(orderFelt.kode).visningsnavn
                put(orderFelt.kode, orderVisningsnavn)
            }
        }

        val rader: List<Map<String, String>> = oppgaver.map { oppgave ->
            buildMap {
                oppgave.hentVerdi("aktorId")?.let { aktørId ->
                    put(
                        "søker", pdlService.person(aktørId).person
                            ?.let { "${it.navn()} ${it.fnr()}" }
                            ?: "Ukjent navn Ukjent fnummer")
                }
                oppgave.hentVerdi("journalpostId")?.let { put("id", it) }
                    ?: oppgave.hentVerdi("saksnummer")?.let { put("id", it) }
                oppgave.hentVerdi("behandlingTypekode")?.let {
                    put("behandlingType", BehandlingType.fraKode(it).navn)
                }
                for ((kolonne, _) in visningskolonner) {
                    if (kolonne !in this) {
                        val verdi = when {
                            orderFelt == null -> null
                            else -> oppgave.hentVerdiEllerListe(orderFelt.område, orderFelt.kode)
                        }
                        verdi?.let { put(kolonne, it.toString()) }
                    }
                }
            }
        }

        return NesteOppgaverFraKoDto(
            kolonner = visningskolonner,
            rader = rader
        )
    }

    @WithSpan
    fun hentKøerForSaksbehandler(
        område: Områder,
        kode6: Boolean,
        saksbehandlerId: Long
    ): List<OppgaveKo> {
        return transactionalManager.transaction { tx ->
            oppgaveKoRepository.hentKoerMedOppgittSaksbehandler(
                område = område,
                skjermet = kode6,
                saksbehandlerId = saksbehandlerId,
                medSaksbehandlere = false,
                tx = tx
            )
        }
    }

    @WithSpan
    suspend fun hentAntallMedOgUtenReserverteForKø(
        område: Områder,
        skjermet: Boolean,
        oppgaveKoId: Long,
    ): AntallOppgaverOgReserverte {
        return coroutineScope {
            val antallUtenReserverte = async(Dispatchers.IO + Span.current().asContextElement()) {
                hentAntallOppgaverForKø(område = område, oppgaveKoId = oppgaveKoId, filtrerReserverte = true, skjermet = skjermet)
            }
            val antallMedReserverte = async(Dispatchers.IO + Span.current().asContextElement()) {
                hentAntallOppgaverForKø(område = område, oppgaveKoId = oppgaveKoId, filtrerReserverte = false, skjermet = skjermet)
            }

            AntallOppgaverOgReserverte(
                antallUtenReserverte.await(),
                antallMedReserverte.await()
            )
        }
    }

    @WithSpan
    fun hentAntallOppgaverForKø(
        område: Områder,
        oppgaveKoId: Long,
        filtrerReserverte: Boolean,
        skjermet: Boolean
    ): Long {
        val ko = oppgaveKoRepository.hent(område, skjermet, oppgaveKoId)
        return antallOppgaverCache.hent(
            AntallOppgaverForKøCacheKey(oppgaveKoId, filtrerReserverte),
            antallOppgaverCacheVarighet
        )
        { oppgaveQueryService.queryForAntall(QueryRequest(område, ko.oppgaveQuery, fjernReserverte = filtrerReserverte)) }
    }

    @WithSpan
    suspend fun taReservasjonFraKø(
        område: Områder,
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
    ): OppgaveMuligReservert {
        return DetaljerMetrikker.timeSuspended("taReservasjonFraKø", "hele", "$oppgaveKoId") {
            doTaReservasjonFraKø(område, innloggetBrukerId, oppgaveKoId)
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

    private suspend fun doTaReservasjonFraKø(
        område: Områder,
        innloggetBrukerId: Long,
        oppgaveKoId: Long,
    ): OppgaveMuligReservert {
        log.info("taReservasjonFraKø, oppgaveKøId: $oppgaveKoId")
        val skjermet = pepClient.harTilgangTilKode6()
        val oppgavekø = DetaljerMetrikker.time("taReservasjonFraKø", "hentKø", "$oppgaveKoId") {
            oppgaveKoRepository.hent(
                område,
                skjermet,
                oppgaveKoId
            )
        }

        var antallKandidaterEtterspurt = 1
        while (true) {
            val kandidatOppgaver = DetaljerMetrikker.time("taReservasjonFraKø", "queryForOppgaveId", "$oppgaveKoId") {
                oppgaveQueryService.queryForOppgave(
                    QueryRequest(
                        område,
                        oppgavekø.oppgaveQuery,
                        fjernReserverte = true,
                        avgrensning = Avgrensning(limit = antallKandidaterEtterspurt.toLong())
                    )
                )
            }
            log.info("Spurte etter $antallKandidaterEtterspurt kandidater fra køen med id $oppgaveKoId, fikk ${kandidatOppgaver.size}")
            val muligReservert = DetaljerMetrikker.timeSuspended("taReservasjonFraKø", "finnReservasjonFraKø", "$oppgaveKoId") {
                transactionalManager.transactionSuspend { tx ->
                    finnReservasjonFraKø(område, kandidatOppgaver, tx, innloggetBrukerId)
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
    private suspend fun finnReservasjonFraKø(
        område: Områder,
        kandidatoppgaver: List<Oppgave>,
        tx: TransactionalSession,
        innloggetBrukerId: Long,
    ): OppgaveMuligReservert {
        for (kandidatoppgave in kandidatoppgaver) {
            try {
                val reservasjon = reservasjonV3Tjeneste.taReservasjon(
                    område = område,
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
    fun hentSaksbehandlereForKo(område: Områder, kode6: Boolean, oppgaveKoId: Long): List<Saksbehandler> {
        val oppgaveKo = oppgaveKoRepository.hent(område, kode6, oppgaveKoId)
        return oppgaveKo.saksbehandlere.mapNotNull { saksbehandlerEpost: String ->
            saksbehandlerRepository.finnSaksbehandlerMedEpost(saksbehandlerEpost, kode6).also {
                if (it == null) {
                    log.info("Køen $oppgaveKoId inneholder saksbehandler som ikke finnes")
                }
            }
        }
    }

    @WithSpan
    suspend fun kopier(
        område: Områder,
        skjermet: Boolean,
        kopierFraOppgaveId: Long,
        tittel: String,
        taMedQuery: Boolean,
        taMedSaksbehandlere: Boolean
    ): OppgaveKo {
        val kø = oppgaveKoRepository.kopier(
            område,
            skjermet,
            kopierFraOppgaveId,
            tittel,
            taMedQuery,
            taMedSaksbehandlere
        )
        køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        return kø
    }

    @WithSpan
    suspend fun leggTil(område: Områder, skjermet: Boolean, tittel: String): OppgaveKo {
        val kø = oppgaveKoRepository.leggTil(område, skjermet, tittel)
        køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
        return kø
    }

    @WithSpan
    fun hent(område: Områder, kode6: Boolean, oppgaveKoId: Long): OppgaveKo {
        return oppgaveKoRepository.hent(område, kode6, oppgaveKoId)
    }

    @WithSpan
    suspend fun slett(område: Områder, kode6: Boolean, oppgaveKoId: Long) {
        oppgaveKoRepository.slett(område, kode6, oppgaveKoId)
        køpåvirkendeHendelseChannel.send(KødefinisjonSlettet(oppgaveKoId))
        antallOppgaverCache.slettForKøId(oppgaveKoId)
    }

    suspend fun endre(område: Områder, skjermet: Boolean, oppgaveKo: OppgaveKo): OppgaveKo {
        val kø = oppgaveKoRepository.endre(område, skjermet, oppgaveKo)
        køpåvirkendeHendelseChannel.send(Kødefinisjon(kø.id))
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
