package no.nav.k9.los.tjenester.mock

import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.util.*


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
    val saksbehandlerRepository: SaksbehandlerRepository by inject()
    val oppgaveKøRepository: OppgaveKøRepository by inject()
    val punsjEventHandler: K9punsjEventHandler by inject()
    val tilbakeEventHandler: K9TilbakeEventHandler by inject()
    val profile: KoinProfile by inject()

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

    fun initTilbakeoppgaver(antall: Int) {
        if (profile == KoinProfile.LOCAL) {
            runBlocking {
                for (i in 0..antall) {
                    val event = BehandlingProsessEventTilbakeDto(
                        eksternId = UUID.randomUUID(),
                        saksnummer = Random().nextInt(0, 200).toString(),
                        behandlingId = 123L,
                        resultatType = null,
                        behandlendeEnhet = null,
                        ansvarligSaksbehandlerIdent = null,
                        opprettetBehandling = LocalDateTime.now(),
                        aktørId = Random().nextInt(0, 9999999).toString(),
                        behandlingStatus = BehandlingStatus.UTREDES.kode,
                        behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode,
                        behandlingTypeKode = "BT-007",
                        behandlingstidFrist = null,
                        eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
                        eventTid = LocalDateTime.now().plusSeconds(i.toLong()),
                        aksjonspunktKoderMedStatusListe = mutableMapOf(AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode to AksjonspunktStatus.OPPRETTET.kode),
                        ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
                        ansvarligBeslutterIdent = null,
                        førsteFeilutbetaling = null,
                        feilutbetaltBeløp = Random().nextLong(1000, 20000),
                        href = null,
                        fagsystem = Fagsystem.K9TILBAKE.kode,
                        behandlinStatus = BehandlingStatus.UTREDES.kode
                    )
                    tilbakeEventHandler.prosesser(event)
                }
            }
        }
    }

    fun initPunsjoppgave() {
        if (profile == KoinProfile.LOCAL) {
            runBlocking {
                punsjEventHandler.prosesser(
                    PunsjEventDto(
                        eksternId = UUID.randomUUID(),
                        journalpostId = JournalpostId("123456789"),
                        eventTid = LocalDateTime.now(),
                        status = Oppgavestatus.AAPEN,
                        aktørId = AktørId("2392173967319"),
                        aksjonspunktKoderMedStatusListe = mutableMapOf(),
                        pleietrengendeAktørId = null,
                        type = "PAPIRSØKNAD",
                        ytelse = "UKJENT",
                        sendtInn = null,
                        ferdigstiltAv = null,
                        journalførtTidspunkt = null
                    )
                )
            }
        }
    }
}

