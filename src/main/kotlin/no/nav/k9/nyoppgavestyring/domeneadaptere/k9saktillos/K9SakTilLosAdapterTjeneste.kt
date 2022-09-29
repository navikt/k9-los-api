package no.nav.k9.nyoppgavestyring.domeneadaptere.k9saktillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import no.nav.k9.Configuration

class K9SakTilLosAdapterTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAdapterTjeneste::class.java)

    fun kjør(kjørSetup: Boolean) {
        if (config.nyOppgavestyringAktivert()) {
            fixedRateTimer(
                name = "k9-sak-til-los",
                daemon = true,
                initialDelay = TimeUnit.SECONDS.toMillis(10),
                period = TimeUnit.DAYS.toMillis(1)
            ) {
                if (kjørSetup) {
                    setup()
                }
                spillAvBehandlingProsessEventer()
            }
        }
    }

    fun spillAvBehandlingProsessEventer() {
        var eventTeller: Long = 0
        log.info("Starter avspilling av BehandlingProsessEventer")

        val startHentAlleIDer = System.currentTimeMillis()
        val behandlingsIder = behandlingProsessEventK9Repository.hentAlleEventerIder()
        log.info("Hentet behandlingsIder, tidsbruk: ${System.currentTimeMillis() - startHentAlleIDer}")

        log.info("Fant ${behandlingsIder.size} behandlinger")
        behandlingsIder.forEach { uuid ->

            val hentEventerForBehandling = System.currentTimeMillis()
            val behandlingProsessEventer = behandlingProsessEventK9Repository.hent(uuid).eventer
            log.info("Hentet eventer for behandling: ${uuid}, tidsbruk: ${System.currentTimeMillis() - hentEventerForBehandling}. Antall eventer: ${behandlingProsessEventer.size}")

            behandlingProsessEventer.forEach { event ->
                val behandlerOppgaveversjon = System.currentTimeMillis()
                val oppgaveDto = lagOppgaveDto(event)

                if (event.behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
                    //val vedtaksInfo = hentVedtaksInfo()
                    //oppgaveDto = oppgaveDto.berikMedVedtaksInfo(vedtaksInfo)
                }

                if (oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto)) { //deilig med destruktiv if-test, eller? :P
                    eventTeller++
                    if (eventTeller.mod(100) == 0) { // Dette logges aldri
                        log.info("Behandlet $eventTeller eventer")
                    }
                }
                log.info("Behandlet oppgaveversjon: ${event.eksternId}, tidsbruk: ${System.currentTimeMillis() - behandlerOppgaveversjon}")
            }
        }
        val (antallAktive, antallAlle) = oppgaveV3Tjeneste.tellAntall()
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    private fun hentVedtaksInfo(): Map<String, String> {
        TODO()
    }

    private fun lagOppgaveDto(event: BehandlingProsessEventDto) =
        OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "aksjonspunkt",
            status = event.aksjonspunktTilstander.lastOrNull()?.status?.kode ?: "OPPR", // TODO statuser må gås opp
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
                verdi = event.resultatType ?: "IKKE_FASTSATT"
            ),
            OppgaveFeltverdiDto(
                nøkkel = "ytelsestype",
                verdi = event.ytelseTypeKode
            ),
            OppgaveFeltverdiDto(
                nøkkel = "behandlingsstatus",
                verdi = event.behandlingStatus ?: "UTRED"
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
            K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )

        val område = områdeRepository.hent(feltdefinisjonerDto.område)!!

        if (!feltdefinisjonTjeneste.hent(område).feltdefinisjoner
                .containsAll(Feltdefinisjoner(feltdefinisjonerDto, område).feltdefinisjoner)
        ) {
            feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
            log.info("opprettet feltdefinisjoner")
        } else {
            log.info("feltdefinisjoner er allerede oppdatert")
        }
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-v2.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )

        val område = områdeRepository.hent(oppgavetyperDto.område)!!

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

    private fun OppgaveDto.berikMedVedtaksInfo(vedtaksInfo: Map<String, String>): OppgaveDto {
        return this.copy(
            feltverdier = this.feltverdier.plus(vedtaksInfo.map { (key, value) ->
                OppgaveFeltverdiDto(nøkkel = key, verdi = value)
            })
        )
    }

}
