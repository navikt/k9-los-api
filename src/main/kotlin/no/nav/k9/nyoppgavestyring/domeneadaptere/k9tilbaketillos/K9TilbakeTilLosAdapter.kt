package no.nav.k9.nyoppgavestyring.domeneadaptere.k9tilbaketillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
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
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveV3
import kotlin.concurrent.thread

class K9TilbakeTilLosAdapter(
    private val behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager
) {

    private val log: Logger = LoggerFactory.getLogger(K9TilbakeTilLosAdapter::class.java)
    private val TRÅDNAVN = "k9-tilbake-til-los"

    companion object {
        private var avspillingKjører = false
    }

    fun kjør(kjørSetup: Boolean = false, kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            if (!avspillingKjører) {
                when (kjørUmiddelbart) {
                    true -> spillAvUmiddelbart()
                    false -> schedulerAvspilling(kjørSetup)
                }
            } else log.info("Avspilling av BehandlingProsessEventer kjører allerede")
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvUmiddelbart() {
        log.info("Spiller av BehandlingProsessEventer umiddelbart")
        avspillingKjører = true
        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN
        ) {
            spillAvBehandlingProsessEventer()
            avspillingKjører = false
        }
    }

    private fun schedulerAvspilling(kjørSetup: Boolean) {
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 10s fra nå, hver 24. time")
        avspillingKjører = true
        fixedRateTimer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.SECONDS.toMillis(10),
            period = TimeUnit.DAYS.toMillis(1)
        ) {
            if (kjørSetup) {
                setup()
            }
            spillAvBehandlingProsessEventer()
            avspillingKjører = false
        }
    }

    private fun spillAvBehandlingProsessEventer() {
        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        var forrigeOppgave: OppgaveV3? = null

        log.info("Starter avspilling av BehandlingProsessEventer (k9-tilbake)")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = behandlingProsessEventTilbakeRepository.hentAlleDirtyEventIder()
        log.info("K9-tilbake: Fant ${behandlingsIder.size} behandlinger")

        behandlingsIder.forEach { uuid ->
            transactionalManager.transaction { tx ->
                val behandlingProsessEventer = behandlingProsessEventTilbakeRepository .hentMedLås(tx, uuid).eventer
                behandlingProsessEventer.forEach { event -> //TODO: hva skjer om eventer kommer out of order her, fordi feks k9 har sendt i feil rekkefølge?
                    val oppgaveDto = lagOppgaveDto(event, forrigeOppgave)

                    if (event.behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
                        //val vedtaksInfo = hentVedtaksInfo()
                        //oppgaveDto = oppgaveDto.berikMedVedtaksInfo(vedtaksInfo)
                    }

                    val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                    oppgave?.let {
                        eventTeller++
                        loggFremgangForHver100(eventTeller, "K9-tilbake: Prosessert $eventTeller eventer")
                    }
                    forrigeOppgave = oppgave
                }
                forrigeOppgave = null
                behandlingTeller++
                loggFremgangForHver100(behandlingTeller, "K9-tilbake: Forsert $behandlingTeller behandlinger")

                behandlingProsessEventTilbakeRepository.fjernDirty(uuid, tx)
            }
        }
        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter kjøring: $antallAlle, antall aktive: $antallAktive, antall nye eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Avspilling av BehandlingProsessEventer (k9-tilbake) ferdig")
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

    private fun hentVedtaksInfo(): Map<String, String> {
        TODO()
    }

    private fun lagOppgaveDto(event: BehandlingProsessEventTilbakeDto, forrigeOppgave: OppgaveV3?) =
        OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9-tilbake",
            status = if (event.aksjonspunktKoderMedStatusListe.values.firstOrNull { status -> status == "OPPR" }.equals(null) ) "UTFO" else "OPPR",
            endretTidspunkt = event.eventTid,
            feltverdier = lagFeltverdier(event, forrigeOppgave)
        )

    private fun lagFeltverdier(
        event: BehandlingProsessEventTilbakeDto,
        forrigeOppgave: OppgaveV3?
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
                verdi = event.fagsystem
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
                nøkkel = "ansvarligSaksbehandlerIdent",
                verdi = event.ansvarligSaksbehandlerIdent ?: forrigeOppgave?.hentVerdi("ansvarligSaksbehandlerIdent")
            ),
            OppgaveFeltverdiDto(
                nøkkel = "mottattDato",
                verdi = forrigeOppgave?.hentVerdi("mottattDato") ?: event.eventTid.toString()
            ),
            OppgaveFeltverdiDto(
                nøkkel = "registrertDato",
                verdi = forrigeOppgave?.hentVerdi("registrertDato") ?: event.eventTid.toString()
            )
        )

        if (event.aksjonspunktKoderMedStatusListe.isNotEmpty()) {
            oppgaveFeltverdiDtos.addAll(event.aksjonspunktKoderMedStatusListe.map { aksjonspunktEntry ->
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = aksjonspunktEntry.key
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

        val åpneAksjonspunkter = event.aksjonspunktKoderMedStatusListe.filter { aksjonspunktEntry ->
            aksjonspunktEntry.value.equals("OPPR")
        }

        if (åpneAksjonspunkter.isNotEmpty()) {
            åpneAksjonspunkter.map { åpentAksjonspunktEntry ->
                oppgaveFeltverdiDtos.add(
                    OppgaveFeltverdiDto(
                        nøkkel = "aktivtAksjonspunkt",
                        verdi = åpentAksjonspunktEntry.key
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
            K9TilbakeTilLosAdapter::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("oppretter feltdefinisjoner")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9TilbakeTilLosAdapter::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-v2.json")!!
                .readText(),
            OppgavetyperDto::class.java
        )
        oppgavetypeTjeneste.oppdater(oppgavetyperDto)
        log.info("opprettet oppgavetype")
    }

    private fun OppgaveDto.berikMedVedtaksInfo(vedtaksInfo: Map<String, String>): OppgaveDto {
        return this.copy(
            feltverdier = this.feltverdier.plus(vedtaksInfo.map { (key, value) ->
                OppgaveFeltverdiDto(nøkkel = key, verdi = value)
            })
        )
    }

}
