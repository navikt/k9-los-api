package no.nav.k9.nyoppgavestyring.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.nyoppgavestyring.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.nyoppgavestyring.feltdefinisjon.Feltdefinisjoner
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
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste
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

    fun spillAvBehandlingProsessEventer(kjørSetup: Boolean) {
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
                            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto)
                        }
                    }
            }
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    private fun lagOppgaveDto(event: BehandlingProsessEventDto, aksjonspunktTilstandDto: AksjonspunktTilstandDto) =
        OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "aksjonspunkt",
            status = aksjonspunktTilstandDto.status.kode,
            endretTidspunkt = event.eventTid,
            feltverdier = lagFeltverdier(event)
        )

    private fun lagFeltverdier(
        event: BehandlingProsessEventDto
    ): List<OppgaveFeltverdiDto> {
        val oppgaveFeltverdiDtos = mutableListOf(
            OppgaveFeltverdiDto(
                nøkkel = "behandlingUuid",
                verdi = event.eksternId.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "aktorId",
                verdi = event.aktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "fagsystem",
                verdi = event.fagsystem.kode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "saksnummer",
                verdi = event.saksnummer
            ),
            OppgaveFeltverdiDto(
                nøkkel = "resultattype",
                verdi = event.resultatType
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ytelsestype",
                verdi = event.ytelseTypeKode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingsstatus",
                verdi = event.behandlingStatus
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingTypekode",
                verdi = event.behandlingTypeKode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "relatertPartAktorid",
                verdi = event.relatertPartAktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "pleietrengendeAktorId",
                verdi = event.pleietrengendeAktørId
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligSaksbehandlerIdent",
                verdi = event.ansvarligSaksbehandlerIdent
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligBeslutterForTotrinn",
                verdi = event.ansvarligBeslutterForTotrinn
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ansvarligSaksbehandlerForTotrinn",
                verdi = event.ansvarligSaksbehandlerIdent
            ),
            OppgaveFeltverdiDto(
                nøkkel = "totrinnskontroll",
                verdi = false.toString() // TODO dette må utledes fra ansvarligBeslutterForTotrinn & ansvarligSaksbehandlerForTotrinn
            )
        )

        if (event.aksjonspunktTilstander.isNotEmpty()) {
            oppgaveFeltverdiDtos.addAll(event.aksjonspunktTilstander.map { aksjonspunktTilstand ->
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = aksjonspunktTilstand.aksjonspunktKode
                )
            })
        } else {
            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = null
                )
            )
        }

        val åpneAksjonspunkter = event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
            aksjonspunktTilstand.status.erÅpentAksjonspunkt()
        }

        if (åpneAksjonspunkter.isNotEmpty()) {
            åpneAksjonspunkter.map { åpentAksjonspunkt ->
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "aktivtAksjonspunkt",
                        verdi = åpentAksjonspunkt.aksjonspunktKode
                    )
                )
            }
        } else {
            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "aktivtAksjonspunkt",
                    verdi = null
                )
            )
        }

        return oppgaveFeltverdiDtos
    }

    private fun setup() {
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
            File("./adapterdefinisjoner/k9-feltdefinisjoner-v2.json"),
            FeltdefinisjonerDto::class.java
        )

        val område = områdeRepository.hent(feltdefinisjonerDto.område)

        if (!feltdefinisjonTjeneste.hent(område).feltdefinisjoner
            .containsAll(Feltdefinisjoner(feltdefinisjonerDto, område).feltdefinisjoner)) {
            feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
            log.info("opprettet feltdefinisjoner")
        } else {
            log.info("feltdefinisjoner er allerede oppdatert")
        }
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            File("./adapterdefinisjoner/k9-oppgavetyper-v2.json"),
            OppgavetyperDto::class.java
        )

        val område = områdeRepository.hent(oppgavetyperDto.område)

        val oppgaveType = oppgavetypeTjeneste.hent(område).oppgavetyper.find {
            it.eksternId.equals(oppgavetyperDto.oppgavetyper.first().id)
        }
        if (oppgaveType == null) {
            oppgavetypeTjeneste.oppdater(oppgavetyperDto)
            log.info("opprettet oppgavetype")
        } else {
            log.info("oppgavetype er allerede oppdatert")
        }
    }

}
