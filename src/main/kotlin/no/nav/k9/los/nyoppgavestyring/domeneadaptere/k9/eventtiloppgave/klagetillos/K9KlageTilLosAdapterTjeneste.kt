package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.JobbMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9KlageTilLosAdapterTjeneste(
    private val k9KlageEventRepository: K9KlageEventRepository,
    private val områdeRepository: OmrådeRepository,
    private val config: Configuration,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val k9klageBeriker: K9KlageBerikerInterfaceKludge,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>
) {

    private val log: Logger = LoggerFactory.getLogger(K9KlageTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-klage-til-los"


    fun kjør(kjørSetup: Boolean = false, kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            when (kjørUmiddelbart) {
                true -> spillAvUmiddelbart()
                false -> schedulerAvspilling(kjørSetup)
            }
        } else log.info("Ny oppgavestyring k9klage er deaktivert")
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
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 2m fra nå, hver time")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.MINUTES.toMillis(2),
            period = TimeUnit.MINUTES.toMillis(1)
        ) {
            if (kjørSetup) {
                setup()
            }
            try {
                JobbMetrikker.time("spill_av_behandlingprosesseventer_k9klage") {
                    spillAvBehandlingProsessEventer()
                }
            } catch (e: Exception) {
                log.warn("Avspilling av k9klage-eventer til oppgaveV3 feilet. Retry om en time", e)
            }
        }
    }

    @WithSpan
    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = k9KlageEventRepository.hentAlleDirtyEventIder()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { uuid ->
            eventTeller = oppdaterOppgaveForBehandlingUuid(uuid, eventTeller)
            behandlingTeller++
            loggFremgangForHver100(behandlingTeller, "Forsert $behandlingTeller behandlinger")
        }

        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter kjøring: $antallAlle, antall aktive: $antallAktive, antall nye eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    fun oppdaterOppgaveForBehandlingUuid(uuid: UUID) {
        oppdaterOppgaveForBehandlingUuid(uuid, 0L)
    }

    @WithSpan
    private fun oppdaterOppgaveForBehandlingUuid(@SpanAttribute uuid: UUID, eventTellerInn: Long): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgave: OppgaveV3? = null
        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = k9KlageEventRepository.hentMedLås(tx, uuid).eventer
            behandlingProsessEventer.forEach { event ->
                val losOpplysningerSomManglerIKlageDto =
                    event.påklagdBehandlingId?.let { k9klageBeriker.hentFraK9Sak(it) }

                val eventBeriket =
                    event.copy(
                        påklagdBehandlingType = event.påklagdBehandlingType ?:
                        event.påklagdBehandlingId?.let {
                            k9klageBeriker.hentFraK9Klage(event.eksternId)?.påklagdBehandlingType
                        },
                        pleietrengendeAktørId = losOpplysningerSomManglerIKlageDto?.pleietrengendeAktørId?.aktørId?.let { AktørId(it.toLong()) },
                        utenlandstilsnitt = losOpplysningerSomManglerIKlageDto?.isUtenlandstilsnitt
                        )

                val oppgaveDto =
                    EventTilDtoMapper.lagOppgaveDto(eventBeriket, forrigeOppgave)

                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                oppgave?.let {
                    eventTeller++
                    loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                }
                forrigeOppgave = oppgave
            }
            forrigeOppgave = null

            runBlocking {
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.K9KLAGE, EksternOppgaveId("K9", uuid.toString())))
            }

            k9KlageEventRepository.fjernDirty(uuid, tx)
        }
        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

    @WithSpan
    fun setup() {
        val objectMapper = jacksonObjectMapper()
        opprettOmråde()
        opprettFeltdefinisjoner(objectMapper)
        opprettOppgavetype(objectMapper)
    }

    private fun opprettOmråde() {
        log.info("oppretter område")
        områdeRepository.lagre("K9")
    }

    private fun opprettFeltdefinisjoner(objectMapper: ObjectMapper) {
        val feltdefinisjonerDto = objectMapper.readValue(
            K9KlageTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("oppretter feltdefinisjoner")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9KlageTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9klage.json")!!
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
