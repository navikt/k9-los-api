package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktType
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventKlageRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import java.util.*
import kotlin.concurrent.thread

class K9KlageTilLosHistorikkvaskTjeneste(
    private val behandlingProsessEventKlageRepository: BehandlingProsessEventKlageRepository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
    private val k9sakBeriker: K9SakBerikerInterfaceKludge,
) {

    private val log: Logger = LoggerFactory.getLogger(K9KlageTilLosHistorikkvaskTjeneste::class.java)
    private val TRÅDNAVN = "k9-klage-til-los-historikkvask"

    fun kjørHistorikkvask() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Starter vask av oppgaver mot historiske k9klage-hendelser")
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
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = behandlingProsessEventKlageRepository.hentAlleEventIderUtenVasketHistorikk()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { uuid ->
            eventTeller = vaskOppgaveForBehandlingUUID(uuid, eventTeller)
            behandlingTeller++
            loggFremgangForHver100(behandlingTeller, "Forsert $behandlingTeller behandlinger")
        }

        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter historikkvask (k9-klage): $antallAlle, antall aktive: $antallAktive, antall nye eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Historikkvask k9klage ferdig")

        behandlingProsessEventKlageRepository.nullstillHistorikkvask()
        log.info("Nullstilt historikkvaskmarkering k9-klage")
    }

    private fun vaskOppgaveForBehandlingUUID(uuid: UUID, eventTellerInn: Long): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgave: OppgaveV3? = null
        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = behandlingProsessEventKlageRepository.hentMedLås(tx, uuid).eventer
            val høyesteInternVersjon =
                oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9-klage", "K9", tx)!!
            var eventNrForBehandling = 0L
            for (event in behandlingProsessEventer) {
                if (eventNrForBehandling > høyesteInternVersjon) { break }
                val losOpplysningerSomManglerIKlageDto = k9sakBeriker.berikKlage(event.påklagdBehandlingEksternId)!!
                val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, losOpplysningerSomManglerIKlageDto, forrigeOppgave)

                oppgaveV3Tjeneste.oppdaterEkstisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, tx)

                eventTeller++
                loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")

                forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                    område = "K9", eksternId = oppgaveDto.id, eksternVersjon = oppgaveDto.versjon, tx = tx
                )
                eventNrForBehandling++
            }
            forrigeOppgave = null

            behandlingProsessEventKlageRepository.markerVasketHistorikk(uuid, tx)
        }
        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

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
            K9KlageTilLosHistorikkvaskTjeneste::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("oppretter feltdefinisjoner")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9KlageTilLosHistorikkvaskTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9klage.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(oppgavetyperDto)
        log.info("opprettet oppgavetype")
    }
}
