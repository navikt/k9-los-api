package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.EventTilDtoMapper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9SakTilLosAdapterTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-sak-til-los"


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
                spillAvBehandlingProsessEventer()
            } catch (e: Exception) {
                log.warn("Avspilling av k9sak-eventer til oppgaveV3 feilet. Retry om en time", e)
            }
        }
    }

    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = behandlingProsessEventK9Repository.hentAlleDirtyEventIder()
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

    private fun oppdaterOppgaveForBehandlingUuid(uuid: UUID, eventTellerInn: Long): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgave: OppgaveV3? = null

        var sisteOppgaveDtoTilHastesakvask: OppgaveDto? = null

        transactionalManager.transaction { tx ->
            val hastesak = oppgaveRepositoryV2.hentMerknader(uuid.toString(), false, tx)
                .filter { merknad -> merknad.merknadKoder.contains("HASTESAK") }.isNotEmpty()
            val behandlingProsessEventer = behandlingProsessEventK9Repository.hentMedLås(tx, uuid).eventer
            val nyeBehandlingsopplysningerFraK9Sak = k9SakBerikerKlient.hentBehandling(uuid)
            behandlingProsessEventer.forEach { event ->
                var oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)
                    .leggTilFeltverdi(
                        OppgaveFeltverdiDto(
                            nøkkel = "hastesak",
                            verdi = hastesak.toString()
                        )
                    )

                oppgaveDto = ryddOppObsoleteOgResultatfeilFra2020(event, oppgaveDto, nyeBehandlingsopplysningerFraK9Sak)

                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                if (oppgave == null) {
                    sisteOppgaveDtoTilHastesakvask = oppgaveDto
                }

                oppgave?.let {
                    eventTeller++
                    loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                }
                forrigeOppgave = oppgave
            }

            // Midlertidig påfunn for å sette markør for hastesak. Mer permanent løsning kommer senere, og da kan dette slettes
            if (sisteOppgaveDtoTilHastesakvask != null) {
                oppgaveV3Tjeneste.oppdaterEkstisterendeOppgaveversjon(sisteOppgaveDtoTilHastesakvask!!, tx)
            }

            forrigeOppgave = null

            behandlingProsessEventK9Repository.fjernDirty(uuid, tx)
        }
        return eventTeller
    }

    internal fun ryddOppObsoleteOgResultatfeilFra2020(
        event: BehandlingProsessEventDto,
        oppgaveDto: OppgaveDto,
        nyeBehandlingsopplysningerFraK9Sak: BehandlingMedFagsakDto?,
    ): OppgaveDto {
        //behandlingen finnes ikke i k9-sak, pga rollback i transaksjon i k9-sak som skulle opprette behandlingen
        if (nyeBehandlingsopplysningerFraK9Sak == null) {
            return oppgaveDto.copy(status = "LUKKET").erstattFeltverdi(
                OppgaveFeltverdiDto(
                    "resultattype", BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode
                )
            )
        }
        if (event.ytelseTypeKode == FagsakYtelseType.OBSOLETE.kode) {
            return oppgaveDto.copy(status = "LUKKET").erstattFeltverdi(
                OppgaveFeltverdiDto(
                    "resultattype", BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode
                )
            )
        }

        if (event.behandlingStatus == "AVSLU"
            && oppgaveDto.feltverdier.filter { it.nøkkel == "resultattype" }.first().verdi == "IKKE_FASTSATT"
        ) {
            if (nyeBehandlingsopplysningerFraK9Sak.sakstype == FagsakYtelseType.OBSOLETE) {
                return oppgaveDto.copy(status = "LUKKET").erstattFeltverdi(
                    OppgaveFeltverdiDto(
                        "resultattype", BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode
                    )
                )
            } else {
                return oppgaveDto.erstattFeltverdi(
                    OppgaveFeltverdiDto(
                        "resultattype", nyeBehandlingsopplysningerFraK9Sak.behandlingResultatType.kode
                    )
                )
            }
        }

        return oppgaveDto
    }

    fun setup(): K9SakTilLosAdapterTjeneste {
        val objectMapper = jacksonObjectMapper()
        opprettOppgavetype(objectMapper)
        return this
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9sak.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(oppgavetyperDto.copy(
            oppgavetyper = oppgavetyperDto.oppgavetyper.map { oppgavetypeDto ->
                oppgavetypeDto.copy(oppgavebehandlingsUrlTemplate = oppgavetypeDto.oppgavebehandlingsUrlTemplate.replace("{baseUrl}", config.k9FrontendUrl()))
            }.toSet()
        ))
        log.info("opprettet oppgavetype")
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
