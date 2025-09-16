package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class K9SakTilLosAdapterTjeneste(
    private val k9SakEventRepository: K9SakEventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val k9SakBerikerKlient: K9SakBerikerInterfaceKludge,
    private val pepCacheService: PepCacheService,
    private val historikkvaskChannel: Channel<k9SakEksternId>,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {

    private val log: Logger = LoggerFactory.getLogger(K9SakTilLosAdapterTjeneste::class.java)

    @WithSpan
    fun spillAvDirtyBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = k9SakEventRepository.hentAlleDirtyEventIder()
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

    @WithSpan
    fun oppdaterOppgaveForBehandlingUuid(@SpanAttribute uuid: UUID, eventTellerInn: Long = 0): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgave: OppgaveV3? = null
        var korrigerFeilRekkefølge = false

        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = k9SakEventRepository.hentMedLås(tx, uuid).eventer
            val nyeBehandlingsopplysningerFraK9Sak = k9SakBerikerKlient.hentBehandling(uuid)
            val høyesteInternVersjon =
                oppgaveV3Tjeneste.hentHøyesteInternVersjon(uuid.toString(), "k9sak", "K9", tx) ?: -1
            var eventNrForBehandling = -1L
            behandlingProsessEventer.forEach { event ->
                eventNrForBehandling++
                var oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(event, forrigeOppgave)
                oppgaveDto = ryddOppObsoleteOgResultatfeilFra2020(event, oppgaveDto, nyeBehandlingsopplysningerFraK9Sak)

                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                //oppgave er satt bare dersom oppgavemodellen ikke har sett eksternVersjon fra tidligere
                //Normaltilfelle når adapter kjøres fra eventhandler er at det kun er ett event å oppdatere
                if (oppgave != null) {
                    pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)

                    // Bruker samme logikk som i v1-modell for å ikke fjerne reservasjoner som midlertidige er på vent med Ventekategori.AVVENTER_ANNET
                    val erPåVent = event.aksjonspunktTilstander.any {
                        it.status.erÅpentAksjonspunkt() && AksjonspunktDefinisjon.fraKode(it.aksjonspunktKode)
                            .erAutopunkt()
                    }
                    if (erPåVent || BehandlingStatus.AVSLUTTET.kode == event.behandlingStatus || oppgave.status == Oppgavestatus.LUKKET) {
                        annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(event, tx)
                    }

                    eventTeller++
                    loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                    if (høyesteInternVersjon >= 1) { //Ikke aktuelt å vaske eventer i feil rekkefølge før man har minst 2 eventer. Telling starter på 0
                        if (eventNrForBehandling <= høyesteInternVersjon) {
                            korrigerFeilRekkefølge = true
                        }
                    }
                    forrigeOppgave = oppgave
                } else {
                    forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon("K9", K9Oppgavetypenavn.SAK.kode, oppgaveDto.eksternId, oppgaveDto.eksternVersjon, tx)
                }
            }
            runBlocking {
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.SAK, EksternOppgaveId("K9", uuid.toString())))
            }

            k9SakEventRepository.fjernDirty(uuid, tx)
        }

        if (korrigerFeilRekkefølge) {
            log.info("OppgaveV3, funnet eventer i feil rekkefølge. Kjører historikkvask for behandlingsUUID: $uuid")
            runBlocking {
                historikkvaskChannel.send(k9SakEksternId(uuid))
            }
        }
        return eventTeller
    }

    private fun annullerReservasjonerHvisAlleOppgaverPåVentEllerAvsluttet(
        event: K9SakEventDto,
        tx: TransactionalSession
    ) {
        val saksbehandlerNøkkel = SakEventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter = false)
        val beslutterNøkkel = SakEventTilOppgaveMapper.utledReservasjonsnøkkel(event, erTilBeslutter = true)
        val antallAnnullert =
            annullerReservasjonHvisAlleOppgaverPåVentEllerAvsluttet(listOf(saksbehandlerNøkkel, beslutterNøkkel), tx)
        if (antallAnnullert > 0) {
            log.info("Annullerte $antallAnnullert reservasjoner maskinelt på oppgave ${event.saksnummer} som følge av status på innkommende event")
        } else {
            log.info("Annullerte ingen reservasjoner på oppgave ${event.saksnummer} som følge av status på innkommende event")
        }
    }

    private fun annullerReservasjonHvisAlleOppgaverPåVentEllerAvsluttet(
        reservasjonsnøkler: List<String>,
        tx: TransactionalSession
    ): Int {
        val åpneOppgaverForReservasjonsnøkkel =
            oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, reservasjonsnøkler)
                .filter { it.status == Oppgavestatus.AAPEN.kode }

        if (åpneOppgaverForReservasjonsnøkkel.isEmpty()) {
            return reservasjonsnøkler.map { reservasjonsnøkkel ->
                reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                    reservasjonsnøkkel,
                    "Maskinelt annullert reservasjon, siden alle oppgaver på reservasjonen står på vent eller er avsluttet",
                    null,
                    tx
                )
            }.count { it }
        }
        log.info("Oppgave annulleres ikke fordi det finnes andre åpne oppgaver: [${åpneOppgaverForReservasjonsnøkkel.map { it.eksternId }}]")
        return 0
    }

    internal fun ryddOppObsoleteOgResultatfeilFra2020(
        event: K9SakEventDto,
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

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
