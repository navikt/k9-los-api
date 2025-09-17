package no.nav.k9.los.tjenester.mock

import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.uttak.SøknadÅrsak
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import no.nav.k9.sak.typer.Periode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.random.Random

val saksbehandlere = listOf(
    Saksbehandler(
        id = null,
        brukerIdent = "Z123456",
        navn = "Saksbehandler Sara",
        epost = "saksbehandler@nav.no",
        reservasjoner = mutableSetOf(),
        enhet = "NAV DRIFT"
    ),
    Saksbehandler(
        id = null,
        brukerIdent = "Z167457",
        navn = "Lars Pokèmonsen",
        epost = "lars.monsen@nav.no",
        reservasjoner = mutableSetOf(),
        enhet = "NAV DRIFT"
    ),
    Saksbehandler(
        id = null,
        brukerIdent = "Z321457",
        navn = "Lord Edgar Hansen",
        epost = "the.lord@nav.no",
        reservasjoner = mutableSetOf(),
        enhet = "NAV DRIFT"
    )
)

object localSetup : KoinComponent {
    private val saksbehandlerRepository: SaksbehandlerRepository by inject()
    private val punsjEventHandler: K9PunsjEventHandler by inject()
    private val tilbakeEventHandler: K9TilbakeEventHandler by inject()
    private val sakEventHandler: K9SakEventHandler by inject()
    private val klageEventHandler: K9KlageEventHandler by inject()
    private val profile: KoinProfile by inject()

    fun initSaksbehandlere() {
        if (profile == KoinProfile.LOCAL) {
            runBlocking {
                saksbehandlere.forEach { saksbehandler ->
                    saksbehandlerRepository.addSaksbehandler(
                        saksbehandler
                    )
                }
            }
        }
    }

    fun initK9SakOppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                val eksternId = UUID.randomUUID()
                val behandlingId = Random.nextLong(0, 2000)
                val saksnummer = behandlingId.toString(36).uppercase().replace("O", "o").replace("I", "i")
                val ytelseTypeKode = listOf(
                    FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                    FagsakYtelseType.PPN,
                    FagsakYtelseType.OLP,
                    FagsakYtelseType.OMSORGSPENGER_AO,
                ).shuffled().first().kode
                val opprettetBehandling = LocalDateTime.now().minusDays(Random.nextLong(10, 20))
                val aktørId = "2392173967319"
                val pleietrengendeAktørId = "1234567890123"
                sakEventHandler.prosesser(
                    K9SakEventDto(
                        eksternId,
                        Fagsystem.SAK,
                        saksnummer,
                        fagsakPeriode = Periode(LocalDate.now().minusMonths(2), LocalDate.now()),
                        behandlingId = behandlingId,
                        fraEndringsdialog = false,
                        resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
                        behandlendeEnhet = null,
                        aksjonspunktTilstander = if (Random.nextBoolean()) listOf(AksjonspunktTilstandDto(
                            AksjonspunktKodeDefinisjon.VENT_PGA_FOR_TIDLIG_SØKNAD_KODE,
                            AksjonspunktStatus.OPPRETTET,
                            Venteårsak.AVV_IM_MOT_SØKNAD_AT,
                            null,
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                        )) else emptyList(),
                        søknadsårsaker = mutableListOf<SøknadÅrsak>().map { it.kode },
                        behandlingsårsaker = mutableListOf<BehandlingÅrsakType>().map { it.kode },
                        ansvarligSaksbehandlerIdent = null as String?,
                        ansvarligBeslutterForTotrinn = null as String?,
                        ansvarligSaksbehandlerForTotrinn = null as String?,
                        opprettetBehandling = opprettetBehandling,
                        vedtaksdato = null,
                        pleietrengendeAktørId = pleietrengendeAktørId,
                        aktørId = aktørId,
                        behandlingStatus = BehandlingStatus.UTREDES.kode,
                        behandlingSteg = BehandlingStegType.KONTROLLER_FAKTA.kode,
                        behandlingTypeKode = no.nav.k9.kodeverk.behandling.BehandlingType.FØRSTEGANGSSØKNAD.kode,
                        behandlingstidFrist = null,
                        eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
                        eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                        aksjonspunktKoderMedStatusListe = mutableMapOf(),
                        ytelseTypeKode = ytelseTypeKode,
                        eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
                        merknader = emptyList()
                    )
                )

                // ferdigstill noen av sakene
                if (Random.nextBoolean()) {
                    sakEventHandler.prosesser(
                        K9SakEventDto(
                            eksternId,
                            Fagsystem.SAK,
                            saksnummer,
                            behandlingId = behandlingId,
                            fraEndringsdialog = false,
                            resultatType = BehandlingResultatType.INNVILGET.kode,
                            aksjonspunktTilstander = emptyList(),
                            søknadsårsaker = mutableListOf<SøknadÅrsak>().map { it.kode },
                            behandlingsårsaker = mutableListOf<BehandlingÅrsakType>().map { it.kode },
                            ansvarligSaksbehandlerIdent = "Z123456",
                            ansvarligBeslutterForTotrinn = "Y123456",
                            ansvarligSaksbehandlerForTotrinn = "Z123456",
                            opprettetBehandling = LocalDateTime.now(),
                            vedtaksdato = LocalDate.now(),
                            pleietrengendeAktørId = pleietrengendeAktørId,
                            aktørId = aktørId,
                            behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
                            behandlingSteg = BehandlingStegType.IVERKSETT_VEDTAK.kode,
                            behandlingTypeKode = no.nav.k9.kodeverk.behandling.BehandlingType.FØRSTEGANGSSØKNAD.kode,
                            behandlingstidFrist = null,
                            eventHendelse = EventHendelse.AKSJONSPUNKT_UTFØRT,
                            eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                            aksjonspunktKoderMedStatusListe = mutableMapOf(),
                            ytelseTypeKode = ytelseTypeKode,
                            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
                            merknader = emptyList()
                        )
                    )
                }
            }
        }
    }

    fun initKlageoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                val event = K9KlageEventDto(
                    eksternId = UUID.randomUUID(),
                    saksnummer = Random.nextInt(0, 200 * antall).toString(),
                    resultatType = null,
                    behandlendeEnhet = null,
                    opprettetBehandling = LocalDateTime.now(),
                    aktørId = Random.nextLong(1_000_000_000_000, 9_000_000_000_000).toString(),
                    behandlingStatus = BehandlingStatus.UTREDES.kode,
                    behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode,
                    behandlingTypeKode = "BT-003",
                    behandlingstidFrist = null,
                    eventHendelse = no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                    påklagdBehandlingId = null,
                    påklagdBehandlingType = null,
                    fagsystem = no.nav.k9.klage.kodeverk.Fagsystem.K9SAK,
                    utenlandstilsnitt = false,
                    ansvarligBeslutter = "",
                    ansvarligSaksbehandler = "",
                    ytelseTypeKode = "PSB",
                    fagsakPeriode = null,
                    pleietrengendeAktørId = null,
                    relatertPartAktørId = null,
                    aksjonspunkttilstander = emptyList(),
                    vedtaksdato = null,
                    behandlingsårsaker = null,
                )
                klageEventHandler.prosesser(event)
            }

        }
    }

    fun initTilbakeoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                val event = K9TilbakeEventDto(
                    eksternId = UUID.randomUUID(),
                    saksnummer = Random.nextInt(0, 200 * antall).toString(),
                    behandlingId = 123L,
                    resultatType = null,
                    behandlendeEnhet = null,
                    ansvarligSaksbehandlerIdent = null,
                    opprettetBehandling = LocalDateTime.now(),
                    aktørId = Random.nextLong(1_000_000_000_000, 9_000_000_000_000).toString(),
                    behandlingStatus = BehandlingStatus.UTREDES.kode,
                    behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode,
                    behandlingTypeKode = "BT-007",
                    behandlingstidFrist = null,
                    eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
                    eventTid = LocalDateTime.now().minusSeconds((antall - i).toLong()),
                    aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode to AksjonspunktStatus.OPPRETTET.kode),
                    ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
                    ansvarligBeslutterIdent = null,
                    førsteFeilutbetaling = LocalDate.now().minusDays(Random.nextLong(100)).toString(),
                    feilutbetaltBeløp = Random.nextLong(1000, 20000),
                    href = null,
                    fagsystem = Fagsystem.TILBAKE.kode,
                )
                tilbakeEventHandler.prosesser(event)
            }
        }
    }

    fun initPunsjoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            for (i in 0..<antall) {
                punsjEventHandler.prosesser(
                    PunsjEventDto(
                        eksternId = UUID.randomUUID(),
                        journalpostId = JournalpostId(Random.nextLong(100000000, 999999999).toString()),
                        eventTid = LocalDateTime.now(),
                        status = Oppgavestatus.VENTER,
                        aktørId = AktørId(Random.nextLong(1_000_000_000_000, 9_000_000_000_000).toString()),
                        aksjonspunktKoderMedStatusListe = mutableMapOf("PUNSJ" to "OPPR"),
                        pleietrengendeAktørId = null,
                        type = BehandlingType.entries.filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE"    }.shuffled().first().kode,
                        ytelse = FagsakYtelseType.entries.shuffled().first().kode,
                        sendtInn = null,
                        ferdigstiltAv = null,
                        journalførtTidspunkt = listOf(LocalDateTime.now(), null).shuffled().first(),
                    )
                )
            }
        }
    }
}
