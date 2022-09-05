package no.nav.k9.nyoppgavestyring.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.nyoppgavestyring.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.nyoppgavestyring.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.nyoppgavestyring.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.oppgave.OppgaveDto
import no.nav.k9.nyoppgavestyring.oppgave.OppgaveFeltverdiDto
import no.nav.k9.nyoppgavestyring.oppgave.OppgaveV3Tjeneste
import no.nav.k9.nyoppgavestyring.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.nyoppgavestyring.oppgavetype.OppgavetyperDto
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

class K9SakTilLosAdapterTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAdapterTjeneste::class.java)

    fun kjør(kjørSetup: Boolean) {

        fixedRateTimer(
            name = "k9-sak-til-los",
            daemon = true,
            initialDelay = TimeUnit.SECONDS.toMillis(10),
            period = TimeUnit.DAYS.toMillis(1)
        ) {
            spillAvBehandlingProsessEventer(kjørSetup)
        }
    }

    private fun spillAvBehandlingProsessEventer(kjørSetup: Boolean) {
        if (kjørSetup) {
            setup()
        }

        log.info("Starter avspilling av BehandlingProsessEventer")
        behandlingProsessEventK9Repository.hentAlleEventerIder()
            .map { uuid ->
                behandlingProsessEventK9Repository.hent(uuid).eventer
                    .map { event ->
                        event.aksjonspunktTilstander.forEach { aksjonspunktTilstand ->
                            val oppgaveDto = lagOppgaveDto(event, aksjonspunktTilstand)
                            oppgaveV3Tjeneste.oppdater(oppgaveDto)
                        }
                    }
            }
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    private fun lagOppgaveDto(event: BehandlingProsessEventDto, aksjonspunktTilstandDto: AksjonspunktTilstandDto) =
        OppgaveDto(
            id = event.eksternId.toString() + "." + aksjonspunktTilstandDto.aksjonspunktKode,
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "aksjonspunkt",
            status = aksjonspunktTilstandDto.status.kode,
            feltverdier = lagFeltverdier(event, aksjonspunktTilstandDto)
        )

    private fun lagFeltverdier(event: BehandlingProsessEventDto, aksjonspunktTilstandDto: AksjonspunktTilstandDto) =
        setOf(
            OppgaveFeltverdiDto(
                nøkkel = "saksnummer",
                verdi = event.saksnummer
            ),
            OppgaveFeltverdiDto(
                nøkkel = "opprettet",
                verdi = event.eventTid.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "aksjonspunktKode",
                verdi = aksjonspunktTilstandDto.aksjonspunktKode
            )
        )

    private fun setup() {
        val objectMapper = jacksonObjectMapper()
        log.info("oppretter område")
        områdeRepository.lagre("K9")
        log.info("oppretter feltdefinisjoner")
        feltdefinisjonTjeneste.oppdater(
            objectMapper.readValue(
                File("./adapterdefinisjoner/k9-feltdefinisjoner-v1.json"),
                FeltdefinisjonerDto::class.java
            )
        )
        log.info("oppretter oppgavetype")
        oppgavetypeTjeneste.oppdater(
            objectMapper.readValue(
                File("./adapterdefinisjoner/k9-oppgavetyper-v1.json"),
                OppgavetyperDto::class.java
            )
        )
    }

}
