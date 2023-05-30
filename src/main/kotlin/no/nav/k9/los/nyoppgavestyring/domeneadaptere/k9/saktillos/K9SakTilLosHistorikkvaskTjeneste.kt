package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerKlient
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.thread

class K9SakTilLosHistorikkvaskTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosHistorikkvaskTjeneste::class.java)
    private val TRÅDNAVN = "k9-sak-til-los-historikkvask"
    private val MANUELLE_AKSJONSPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
        aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.MANUELL
    }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

    private val AUTOPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
        aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.AUTOPUNKT
    }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9sak-hendelser")
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN
            ) {
                spillAvBehandlingProsessEventer()
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av historiske BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = behandlingProsessEventK9Repository.hentAlleEventIderUtenVasketHistorikk()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { uuid ->
            eventTeller = vaskOppgaverForBehandlingUUID(uuid, eventTeller)
            behandlingTeller++
            loggFremgangForHver100(behandlingTeller, "Vasket $behandlingTeller behandlinger")
        }

        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter historikkvask (k9-sak): $antallAlle, antall aktive: $antallAktive, antall vaskede eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Historikkvask k9sak ferdig")

        behandlingProsessEventK9Repository.nullstillHistorikkvask()
    }

    private fun vaskOppgaverForBehandlingUUID(uuid: UUID, eventTellerInn: Long): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgave: OppgaveV3? = null
        transactionalManager.transaction { tx ->
            val hastesak = oppgaveRepositoryV2.hentMerknader(uuid.toString(), false)
                .filter { merknad -> merknad.merknadKoder.contains("HASTESAK") }.isNotEmpty()
            val behandlingProsessEventer = behandlingProsessEventK9Repository.hentMedLås(tx, uuid).eventer
            behandlingProsessEventer.forEach { event ->
                var oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)
                    .leggTilFeltverdi(
                        OppgaveFeltverdiDto(
                            nøkkel = "hastesak",
                            verdi = hastesak.toString()
                        )
                    )

                oppgaveDto = ryddOppResultatfeilFra2020(event, oppgaveDto)

                oppgaveV3Tjeneste.oppdaterEkstisterendeOppgaveversjon(oppgaveDto, tx)

                eventTeller++
                loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")

                forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                    område = "K9", eksternId = oppgaveDto.id, eksternVersjon = oppgaveDto.versjon, tx = tx
                )
            }
            forrigeOppgave = null

            behandlingProsessEventK9Repository.markerVasketHistorikk(uuid, tx)
        }
        return eventTeller
    }

    private fun ryddOppResultatfeilFra2020(
        event: BehandlingProsessEventDto,
        oppgaveDto: OppgaveDto
    ): OppgaveDto {
        var oppgaveDto1 = oppgaveDto
        if (event.behandlingStatus == "AVSLU" && event.resultatType == null) {
            oppgaveDto1 = OppgaveDto(
                oppgaveDto1,
                feltverdier = oppgaveDto1.feltverdier
                    .filterNot { it.nøkkel == "resultattype" }
                    .plus(
                        OppgaveFeltverdiDto(
                            nøkkel = "resultattype",
                            verdi = k9SakBerikerKlient.hentBehandling(event.eksternId!!).behandlingResultatType.kode
                        )
                    )
            )
        }
        return oppgaveDto1
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

    fun setup(): K9SakTilLosHistorikkvaskTjeneste {
        val objectMapper = jacksonObjectMapper()
        opprettOmråde()
        opprettFeltdefinisjoner(objectMapper)
        opprettOppgavetype(objectMapper)
        return this
    }

    private fun opprettOmråde() {
        log.info("oppretter område")
        områdeRepository.lagre("K9")
    }

    private fun opprettFeltdefinisjoner(objectMapper: ObjectMapper) {
        val feltdefinisjonerDto = objectMapper.readValue(
            K9SakTilLosHistorikkvaskTjeneste::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("oppretter feltdefinisjoner")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9SakTilLosHistorikkvaskTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9sak.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(oppgavetyperDto)
        log.info("opprettet oppgavetype")
    }
}
