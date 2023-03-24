package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
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
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class K9SakTilLosAdapterTjeneste(
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonTjeneste: FeltdefinisjonTjeneste,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-sak-til-los"
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
        log.info("Schedulerer avspilling av BehandlingProsessEventer til å kjøre 1m fra nå, hver time")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.HOURS.toMillis(1),
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
        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = behandlingProsessEventK9Repository.hentMedLås(tx, uuid).eventer
            behandlingProsessEventer.forEach { event ->
                val oppgaveDto = lagOppgaveDto(event, forrigeOppgave)

                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                oppgave?.let {
                    eventTeller++
                    loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                }
                forrigeOppgave = oppgave
            }
            forrigeOppgave = null

            behandlingProsessEventK9Repository.fjernDirty(uuid, tx)
        }
        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }


    private fun oppgaveSkalHaVentestatus(event: BehandlingProsessEventDto): Boolean {
        val åpneAksjonspunkter = event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
            aksjonspunktTilstand.status.erÅpentAksjonspunkt()
        }

        val ventetype = utledVentetype(event.behandlingSteg, åpneAksjonspunkter)
        return ventetype != Ventekategori.AVVENTER_SAKSBEHANDLER
    }

    private fun lagOppgaveDto(event: BehandlingProsessEventDto, forrigeOppgave: OppgaveV3?) =
        OppgaveDto(
            id = event.eksternId.toString(),
            versjon = event.eventTid.toString(),
            område = "K9",
            kildeområde = "K9",
            type = "k9sak",
            status = if (event.aksjonspunktTilstander.any { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.status.erÅpentAksjonspunkt() }) {
                if (oppgaveSkalHaVentestatus(event)) {
                    "VENTER"
                } else {
                    "AAPEN"
                }
            } else {
                if (event.behandlingStatus != BehandlingStatus.AVSLUTTET.getKode() && event.behandlingStatus != BehandlingStatus.IVERKSETTER_VEDTAK.getKode()) {
                    "AAPEN"
                } else {
                    "LUKKET"
                }
            },
            endretTidspunkt = event.eventTid,
            feltverdier = lagFeltverdier(event, forrigeOppgave)
        )

    private fun lagFeltverdier(
        event: BehandlingProsessEventDto,
        forrigeOppgave: OppgaveV3?
    ): List<OppgaveFeltverdiDto> {
        val oppgaveFeltverdiDtos = mapEnkeltverdier(event, forrigeOppgave)

        val åpneAksjonspunkter = event.aksjonspunktTilstander.filter { aksjonspunktTilstand ->
            aksjonspunktTilstand.status.erÅpentAksjonspunkt()
        }

        val harManueltAksjonspunkt = åpneAksjonspunkter.any { aksjonspunktTilstandDto ->
            MANUELLE_AKSJONSPUNKTER.contains(aksjonspunktTilstandDto.aksjonspunktKode)
        }

        utledAksjonspunkter(event, oppgaveFeltverdiDtos)
        utledÅpneAksjonspunkter(event.behandlingSteg, åpneAksjonspunkter, oppgaveFeltverdiDtos)
        utledVenteÅrsakOgFrist(åpneAksjonspunkter, oppgaveFeltverdiDtos)
        utledAutomatiskBehandletFlagg(forrigeOppgave, oppgaveFeltverdiDtos, harManueltAksjonspunkt)
        oppgaveFeltverdiDtos.addAll(ventekategoriTilFlagg(utledVentetype(event.behandlingSteg, åpneAksjonspunkter)))

        return oppgaveFeltverdiDtos
    }

    private fun mapEnkeltverdier(
        event: BehandlingProsessEventDto,
        forrigeOppgave: OppgaveV3?
    ) = mutableListOf(
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
            nøkkel = "ansvarligBeslutter",
            verdi = event.ansvarligBeslutterForTotrinn ?: forrigeOppgave?.hentVerdi("ansvarligBeslutter")
        ),
        OppgaveFeltverdiDto(
            nøkkel = "ansvarligSaksbehandler",
            verdi = event.ansvarligSaksbehandlerForTotrinn
                ?: forrigeOppgave?.hentVerdi("ansvarligSaksbehandler")
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
        event.nyeKrav?.let {
            OppgaveFeltverdiDto(
                nøkkel = "nyeKrav",
                verdi = event.nyeKrav.toString()
            )
        },
        event.fraEndringsdialog?.let {
            OppgaveFeltverdiDto(
                nøkkel = "fraEndringsdialog",
                verdi = event.fraEndringsdialog.toString()
            )
        },
        OppgaveFeltverdiDto(
            nøkkel = "totrinnskontroll",
            verdi = event.aksjonspunktTilstander.filter { aksjonspunktTilstandDto ->
                aksjonspunktTilstandDto.aksjonspunktKode.equals("5015") && aksjonspunktTilstandDto.status !in (listOf(
                    AksjonspunktStatus.AVBRUTT
                ))
            }.isNotEmpty().toString()
        )
    ).filterNotNull().toMutableList()

    internal fun utledVentetype(
        behandlingSteg: String?,
        åpneAksjonspunkter: List<AksjonspunktTilstandDto>
    ): Ventekategori? {
        if (behandlingSteg.isNullOrEmpty()) {
            if (åpneAksjonspunkter.isEmpty()) {
                return null
            } else {
                throw IllegalStateException("Aktivt aksjonspunkt: ${åpneAksjonspunkter.first().aksjonspunktKode}, men ikke aktivt behandlingssteg")
            }
        }

        if (åpneAksjonspunkter.isEmpty()) {
            return Ventekategori.AVVENTER_ANNET
        }

        val førsteAPMedFristOgVenteårsak = åpneAksjonspunkter
            .filter { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.fristTid != null }
            .sortedBy { aksjonspunktTilstandDto -> aksjonspunktTilstandDto.fristTid }
            .firstOrNull()

        if (førsteAPMedFristOgVenteårsak != null) {
            return førsteAPMedFristOgVenteårsak.venteårsak.ventekategori
        }


        val ventekategorierPrioritert = listOf(
            Ventekategori.AVVENTER_TEKNISK_FEIL,
            Ventekategori.AVVENTER_SAKSBEHANDLER,
            Ventekategori.AVVENTER_ANNET,
            Ventekategori.AVVENTER_ARBEIDSGIVER,
            Ventekategori.AVVENTER_SØKER,
            Ventekategori.AVVENTER_ANNET_IKKE_SAKSBEHANDLINGSTID
        )

        //Hvis ingen venteårsak hentes ventekategori fra løsbare aksjonspunkt i prioritert rekkefølge
        val løsbareAksjonspunkt = åpneAksjonspunkter.filter { åpentAksjonspunkt ->
            val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
            aksjonspunktDefinisjon.erAutopunkt() || aksjonspunktDefinisjon.behandlingSteg != null && aksjonspunktDefinisjon.behandlingSteg.kode == behandlingSteg
        }

        ventekategorierPrioritert.forEach { ventekategori ->
            if (apInneholder(løsbareAksjonspunkt, ventekategori)) {
                return ventekategori
            }
        }

        return Ventekategori.AVVENTER_ANNET
    }

    internal fun ventekategoriTilFlagg(
        ventekategori: Ventekategori?
    ): List<OppgaveFeltverdiDto> {
        if (ventekategori == null) {
            return avventerIngen()
        }
        return when (ventekategori) {
            Ventekategori.AVVENTER_SØKER -> avventerSøker()
            Ventekategori.AVVENTER_ARBEIDSGIVER -> avventerArbeidsgiver()
            Ventekategori.AVVENTER_SAKSBEHANDLER -> avventerSaksbehandler()
            Ventekategori.AVVENTER_TEKNISK_FEIL -> avventerTekniskFeil()
            Ventekategori.AVVENTER_ANNET -> avventerAnnet()
            Ventekategori.AVVENTER_ANNET_IKKE_SAKSBEHANDLINGSTID -> avventerAnnetIkkeSaksbehandlingstid()
            else -> throw IllegalArgumentException("Ukjent ventekategori: ${ventekategori}")
        }
    }

    private fun apInneholder(
        løsbareAksjonspunkt: List<AksjonspunktTilstandDto>,
        ventekategori: Ventekategori
    ): Boolean {
        return løsbareAksjonspunkt.firstOrNull { aksjonspunktTilstandDto ->
            AksjonspunktDefinisjon.fraKode(aksjonspunktTilstandDto.aksjonspunktKode).defaultVentekategori == ventekategori
        } != null
    }

    internal fun avventerSøker(): List<OppgaveFeltverdiDto> {
        return avventerflagg("avventerSøker")
    }

    internal fun avventerArbeidsgiver(): List<OppgaveFeltverdiDto> {
        return avventerflagg("avventerArbeidsgiver")
    }

    internal fun avventerSaksbehandler(): List<OppgaveFeltverdiDto> {
        return avventerflagg("avventerSaksbehandler")
    }

    internal fun avventerTekniskFeil(): List<OppgaveFeltverdiDto> {
        return avventerflagg("avventerTekniskFeil")
    }

    internal fun avventerAnnet(): List<OppgaveFeltverdiDto> {
        return avventerflagg("avventerAnnet")
    }

    internal fun avventerAnnetIkkeSaksbehandlingstid(): List<OppgaveFeltverdiDto> {
        return avventerflagg("avventerAnnetIkkeSaksbehandlingstid")
    }

    internal fun avventerIngen(): List<OppgaveFeltverdiDto> {
        return avventerflagg("")
    }

    private fun avventerflagg(skalSettesTrue: String): List<OppgaveFeltverdiDto> {
        val oppgavefelter = mutableListOf<OppgaveFeltverdiDto>()
        listOf(
            "avventerSøker",
            "avventerArbeidsgiver",
            "avventerSaksbehandler",
            "avventerTekniskFeil",
            "avventerAnnet",
            "avventerAnnetIkkeSaksbehandlingstid"
        ).forEach {
            if (skalSettesTrue == it) {
                oppgavefelter.add(
                    OppgaveFeltverdiDto(
                        nøkkel = it,
                        verdi = true.toString()
                    )
                )
            } else {
                oppgavefelter.add(
                    OppgaveFeltverdiDto(
                        nøkkel = it,
                        verdi = false.toString()
                    )
                )
            }
        }

        return oppgavefelter
    }

    private fun utledAutomatiskBehandletFlagg(
        forrigeOppgave: OppgaveV3?,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>,
        harManueltAksjonspunkt: Boolean
    ) {
        if (forrigeOppgave != null && forrigeOppgave.hentVerdi("helautomatiskBehandlet").toBoolean().not()) {
            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "helautomatiskBehandlet",
                    verdi = false.toString()
                )
            )
        } else {
            oppgaveFeltverdiDtos.add(
                OppgaveFeltverdiDto(
                    nøkkel = "helautomatiskBehandlet",
                    verdi = if (harManueltAksjonspunkt) false.toString() else true.toString()
                )
            )
        }
    }

    private fun utledÅpneAksjonspunkter(
        behandlingSteg: String?,
        åpneAksjonspunkter: List<AksjonspunktTilstandDto>,
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
            if (behandlingSteg != null) {
                åpneAksjonspunkter.firstOrNull { åpentAksjonspunkt ->
                    val aksjonspunktDefinisjon = AksjonspunktDefinisjon.fraKode(åpentAksjonspunkt.aksjonspunktKode)
                    !aksjonspunktDefinisjon.erAutopunkt() && aksjonspunktDefinisjon.behandlingSteg != null && aksjonspunktDefinisjon.behandlingSteg.kode == behandlingSteg
                }?.let {
                    oppgaveFeltverdiDtos.add(
                        OppgaveFeltverdiDto(
                            nøkkel = "løsbartAksjonspunkt",
                            verdi = it.aksjonspunktKode
                        )
                    )
                }
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

    private fun utledVenteÅrsakOgFrist(
        åpneAksjonspunkter: List<AksjonspunktTilstandDto>,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
    ) {
        if (åpneAksjonspunkter.isNotEmpty()) {
            åpneAksjonspunkter
                .filter { aksjonspunktTilstandDto ->
                    aksjonspunktTilstandDto.venteårsak != Venteårsak.UDEFINERT
                            && aksjonspunktTilstandDto.status == AksjonspunktStatus.OPPRETTET
                }
                .singleOrNull { aksjonspunktTilstandDto ->
                    oppgaveFeltverdiDtos.add(
                        OppgaveFeltverdiDto(
                            nøkkel = "aktivVenteårsak",
                            verdi = aksjonspunktTilstandDto.venteårsak.kode.toString()
                        )
                    )
                    oppgaveFeltverdiDtos.add(
                        OppgaveFeltverdiDto(
                            nøkkel = "aktivVentefrist",
                            verdi = aksjonspunktTilstandDto.fristTid.toString()
                        )
                    )
                }
        }
    }

    private fun utledAksjonspunkter(
        event: BehandlingProsessEventDto,
        oppgaveFeltverdiDtos: MutableList<OppgaveFeltverdiDto>
    ) {
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
    }

    fun setup(): K9SakTilLosAdapterTjeneste {
        if (LocalDateTime.now().isBefore(LocalDateTime.of(2023, 3, 24, 18, 30))) {
            oppgaveV3Tjeneste.destruktivSlettAvAlleOppgaveData()
        }
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
            K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-feltdefinisjoner-v2.json")!!
                .readText(),
            FeltdefinisjonerDto::class.java
        )
        log.info("oppretter feltdefinisjoner")
        feltdefinisjonTjeneste.oppdater(feltdefinisjonerDto)
    }

    private fun opprettOppgavetype(objectMapper: ObjectMapper) {
        val oppgavetyperDto = objectMapper.readValue(
            K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/k9-oppgavetyper-k9sak.json")!!
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
