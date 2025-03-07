package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9KlageEventDto
import no.nav.k9.los.jobber.JobbMetrikker
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheService
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9TilbakeTilLosAdapterTjeneste(
    private val behandlingProsessEventTilbakeRepository: K9TilbakeEventRepository,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val pepCacheService: PepCacheService,
    private val historikkvaskChannel: Channel<k9TilbakeEksternId>
) {

    private val log: Logger = LoggerFactory.getLogger(K9TilbakeTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-tilbake-til-los"


    fun kjør(kjørSetup: Boolean = false, kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            when (kjørUmiddelbart) {
                true -> spillAvUmiddelbart()
                false -> schedulerAvspilling(kjørSetup)
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvUmiddelbart() {
        log.info("Spiller av BehandlingProsessEventer umiddelbart")
        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN
        ) {
            spillAvBehandlingProsessEventer()
        }
    }

    private fun schedulerAvspilling(kjørSetup: Boolean) {
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 1m fra nå, hver time")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.MINUTES.toMillis(1),
            period = TimeUnit.HOURS.toMillis(1)
        ) {
            if (kjørSetup) {
                setup()
            }
            try {
                JobbMetrikker.time("spill_av_behandlingprosesseventer_k9tilbake") {
                    spillAvBehandlingProsessEventer()
                }
            } catch (e: Exception) {
                log.warn("Avspilling av k9tilbake-eventer til oppgaveV3 feilet. Retry om en time", e)
            }
        }
    }

    @WithSpan
    fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")

        val behandlingsIder = behandlingProsessEventTilbakeRepository.hentAlleDirtyEventIder()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        behandlingsIder.forEach { uuid ->
            oppdaterOppgaveForBehandlingUuid(uuid)
        }

        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        log.info("Antall oppgaver etter kjøring: $antallAlle, antall aktive: $antallAktive")
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    @WithSpan
    fun oppdaterOppgaveForBehandlingUuid(@SpanAttribute uuid: UUID) {
        var forrigeOppgave: OppgaveV3? = null
        var korrigerFeilRekkefølge = false

        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = behandlingProsessEventTilbakeRepository.hentMedLås(tx, uuid).eventer
            val høyesteInternVersjon = oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9tilbake", "K9", tx) ?: -1
            var eventNrForBehandling = -1L
            behandlingProsessEventer.forEach { event ->
                eventNrForBehandling++
                val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)
                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                //oppgave er satt bare dersom oppgavemodellen ikke har sett eksternVersjon fra tidligere
                //Normaltilfelle når adapter kjøres fra eventhandler er at det kun er ett event å oppdatere
                if (oppgave != null) {
                    pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)

                    // Bruker samme logikk som i v1-modell for å ikke fjerne reservasjoner som midlertidige er på vent med Ventekategori.AVVENTER_ANNET
                    val erPåVent = event.aksjonspunktKoderMedStatusListe.any { it.value == AksjonspunktStatus.OPPRETTET.kode && AksjonspunktDefinisjonK9Tilbake.fraKode(it.key).erAutopunkt }
                    if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
                        annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(event, tx)
                    }

                    if (høyesteInternVersjon >= 1 && eventNrForBehandling <= høyesteInternVersjon) {
                        korrigerFeilRekkefølge = true
                    }
                    forrigeOppgave = oppgave
                } else {
                    forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon("K9", oppgaveDto.id, oppgaveDto.versjon, tx)
                }
            }

            behandlingProsessEventTilbakeRepository.fjernDirty(uuid, tx)
        }

        if (korrigerFeilRekkefølge) {
            log.info("OppgaveV3, funnet eventer i feil rekkefølge. Kjører historikkvask for behandlingsUUID: $uuid")
            runBlocking {
                historikkvaskChannel.send(k9TilbakeEksternId(uuid))
            }
        }
    }

    private fun annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(
        event: K9KlageEventDto,
        tx: TransactionalSession
    ) {
        val saksbehandlerNøkkel = EventTilDtoMapper.utledReservasjonsnøkkel(event, erTilBeslutter = false)
        val beslutterNøkkel = EventTilDtoMapper.utledReservasjonsnøkkel(event, erTilBeslutter = true)
        val antallAnnullert = annullerReservasjonHvisAlleOppgaverPåVentEllerAvsluttet(listOf(saksbehandlerNøkkel, beslutterNøkkel), tx)
        if (antallAnnullert > 0) {
            log.info("Annullerte $antallAnnullert reservasjoner maskinelt på oppgave ${event.saksnummer} som følge av status på innkommende event")
        } else {
            log.info("Annullerte ingen reservasjoner på oppgave ${event.saksnummer} som følge av status på innkommende event")
        }
    }

    private fun annullerReservasjonHvisAlleOppgaverPåVentEllerAvsluttet(
        reservasjonsnøkler: List<String>,
        tx: TransactionalSession
    ): Int {
        val åpneOppgaverForReservasjonsnøkkel =
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkler)
                .filter { it.status == Oppgavestatus.AAPEN.kode }

        if (åpneOppgaverForReservasjonsnøkkel.isEmpty()) {
            return reservasjonsnøkler.map { reservasjonsnøkkel ->
                reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                    reservasjonsnøkkel,
                    "Maskinelt annullert reservasjon, siden alle oppgaver på reservasjonen står på vent eller er avsluttet",
                    null,
                    tx
                )
            }.count { it }
        }
        log.info("Oppgave annulleres ikke fordi det finnes andre åpne oppgaver: [${åpneOppgaverForReservasjonsnøkkel.map { it.eksternId }}]")
        return 0
    }

    @WithSpan
    fun setup(): K9TilbakeTilLosAdapterTjeneste {
        val objectMapper = jacksonObjectMapper()
        opprettOppgavetype(objectMapper)
        return this
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9TilbakeTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9tilbake.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(
            oppgavetyperDto.copy(
                oppgavetyper = oppgavetyperDto.oppgavetyper.map { oppgavetypeDto ->
                    oppgavetypeDto.copy(
                        oppgavebehandlingsUrlTemplate = oppgavetypeDto.oppgavebehandlingsUrlTemplate.replace(
                            "{baseUrl}",
                            config.k9FrontendUrl()
                        )
                    )
                }.toSet()
            )
        )
        log.info("opprettet oppgavetype")
    }

}
