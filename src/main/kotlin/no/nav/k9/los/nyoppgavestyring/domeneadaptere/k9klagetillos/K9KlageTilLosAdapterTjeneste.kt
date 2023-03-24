package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
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
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventKlageRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9KlageTilLosAdapterTjeneste(
    private val behandlingProsessEventKlageRepository: BehandlingProsessEventKlageRepository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager
) {

    private val log: Logger = LoggerFactory.getLogger(K9KlageTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-klage-til-los"
    private val MANUELLE_AKSJONSPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
        aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.MANUELL
    }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

    private val AUTOPUNKTER = AksjonspunktDefinisjon.values().filter { aksjonspunktDefinisjon ->
        aksjonspunktDefinisjon.aksjonspunktType == AksjonspunktType.AUTOPUNKT
    }.map { aksjonspunktDefinisjon -> aksjonspunktDefinisjon.kode }

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
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 2m fra nå, hver time")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.MINUTES.toMillis(2),
            period = TimeUnit.HOURS.toMillis(1)
        ) {
            if (kjørSetup) {
                setup()
            }
            try {
                spillAvBehandlingProsessEventer()
            } catch (e: Exception) {
                log.warn("Avspilling av k9klage-eventer til oppgaveV3 feilet. Retry om en time", e)
            }
        }
    }

    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = behandlingProsessEventKlageRepository.hentAlleDirtyEventIder()
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
        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = behandlingProsessEventKlageRepository.hentMedLås(tx, uuid).eventer
            behandlingProsessEventer.forEach { event -> //TODO: hva skjer om eventer kommer out of order her, fordi feks k9 har sendt i feil rekkefølge?
                val oppgaveDto = lagOppgaveDto(event, forrigeOppgave)

                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                oppgave?.let {
                    eventTeller++
                    loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                }
                forrigeOppgave = oppgave
            }
            forrigeOppgave = null

            behandlingProsessEventKlageRepository.fjernDirty(uuid, tx)
        }
        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }

    private fun lagOppgaveDto(event: KlagebehandlingProsessHendelse, forrigeOppgave: OppgaveV3?) =
        OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9klage",
            status = if (event.aksjonspunkttilstander.any { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status.erÅpentAksjonspunkt() }) {
                if (oppgaveSkalHaVentestatus(event)) {
                    "VENTER"
                } else {
                    "AAPEN"
                }
            } else {
                if (event.behandlingStatus == BehandlingStatus.UTREDES.toString()) {
                    "AAPEN"
                } else {
                    "LUKKET"
                }
            },
            endretTidspunkt = event.eventTid,
            feltverdier = lagFeltverdier(event, forrigeOppgave)
        )

    private fun oppgaveSkalHaVentestatus(event: KlagebehandlingProsessHendelse): Boolean {
        val oppgaveFeltverdiDtos = mutableListOf<OppgaveFeltverdiDto>()
        val åpneAksjonspunkter = event.aksjonspunkttilstander.filter { aksjonspunkttilstand ->
            aksjonspunkttilstand.status.erÅpentAksjonspunkt()
        }

        val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
            AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
        }

        val harManueltAksjonspunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
            MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
        }

        utledAvventerSaksbehandler(harManueltAksjonspunkt = harManueltAksjonspunkt, harAutopunkt = harAutopunkt, oppgaveFeltverdiDtos = oppgaveFeltverdiDtos)

        return oppgaveFeltverdiDtos.first().nøkkel == "false"
    }

    private fun lagFeltverdier(
        event: KlagebehandlingProsessHendelse,
        forrigeOppgave: OppgaveV3?
    ): List<OppgaveFeltverdiDto> {
        val oppgaveFeltverdiDtos = mapEnkeltverdier(event, forrigeOppgave)

        val åpneAksjonspunkter = event.aksjonspunkttilstander.filter { aksjonspunkttilstand ->
            aksjonspunkttilstand.status.erÅpentAksjonspunkt()
        }

        val harAutopunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
            AUTOPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
        }

        val harManueltAksjonspunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
            MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
        }

        utledAksjonspunkter(event, oppgaveFeltverdiDtos)
        utledÅpneAksjonspunkter(åpneAksjonspunkter, oppgaveFeltverdiDtos)
        //automatiskBehandletFlagg er defaultet til False p.t.
        utledAvventerSaksbehandler(harManueltAksjonspunkt, harAutopunkt, oppgaveFeltverdiDtos)

        return oppgaveFeltverdiDtos
    }

    private fun utledÅpneAksjonspunkter(
        åpneAksjonspunkter: List<Aksjonspunkttilstand>,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
    ) {
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
    }

    private fun utledAksjonspunkter(
        event: KlagebehandlingProsessHendelse,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
    ) {
        if (event.aksjonspunkttilstander.isNotEmpty()) {
            oppgaveFeltverdiDtos.addAll(event.aksjonspunkttilstander.map { aksjonspunkttilstand ->
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = aksjonspunkttilstand.aksjonspunktKode
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
    }

    private fun mapEnkeltverdier(
        event: KlagebehandlingProsessHendelse,
        forrigeOppgave: OppgaveV3?
    ) = mutableListOf(
        OppgaveFeltverdiDto(
            nøkkel = "behandlingUuid",
            verdi = event.eksternId.toString()
        ),
        OppgaveFeltverdiDto(
            nøkkel = "påklagdBehandlingUuid",
            verdi = event.påklagdBehandlingEksternId.toString(),
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
            verdi = event.relatertPartAktørId?.id
        ),
        OppgaveFeltverdiDto(
            nøkkel = "pleietrengendeAktorId",
            verdi = event.pleietrengendeAktørId?.id
        ),
        OppgaveFeltverdiDto(
            nøkkel = "ansvarligSaksbehandler",
            verdi = event.ansvarligSaksbehandler ?: forrigeOppgave?.hentVerdi("ansvarligSaksbehandler")
        ),
        OppgaveFeltverdiDto(
            nøkkel = "ansvarligBeslutter",
            verdi = event.ansvarligBeslutter ?: forrigeOppgave?.hentVerdi("ansvarligBeslutter")
        ),
        OppgaveFeltverdiDto(
            nøkkel = "mottattDato",
            verdi = forrigeOppgave?.hentVerdi("mottattDato") ?: event.eventTid.toString()
        ),
        OppgaveFeltverdiDto(
            nøkkel = "registrertDato",
            verdi = forrigeOppgave?.hentVerdi("registrertDato") ?: event.eventTid.toString()
        ),
        OppgaveFeltverdiDto(
            nøkkel = "vedtaksdato",
            verdi = event.vedtaksdato?.toString() ?: forrigeOppgave?.hentVerdi("vedtaksdato")
        ),
        OppgaveFeltverdiDto(
            nøkkel = "totrinnskontroll",
            verdi = event.aksjonspunkttilstander.filter { aksjonspunktTilstandDto ->
                aksjonspunktTilstandDto.aksjonspunktKode.equals("5015") && aksjonspunktTilstandDto.status.equals(no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus.AVBRUTT).not()
            }.isNotEmpty().toString()
        ),
        OppgaveFeltverdiDto(
            nøkkel = "helautomatiskBehandlet",
            verdi = false.toString() //TODO: Påstand - klagesaker er alltid manuelt behandlet?
        )
    )

    private fun utledAvventerSaksbehandler(
        harManueltAksjonspunkt: Boolean,
        harAutopunkt: Boolean,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
    ) {
        if (harManueltAksjonspunkt && !harAutopunkt) {
            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "true"
                )
            )
        } else {
            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "false"
                )
            )
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
        oppgavetypeTjeneste.oppdater(oppgavetyperDto)
        log.info("opprettet oppgavetype")
    }
}
