package no.nav.k9.los.tjenester.mock

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
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

    fun initPunsjoppgave() {
        if (profile == KoinProfile.LOCAL) {
            runBlocking {
                punsjEventHandler.prosesser(PunsjEventDto(
                    eksternId = UUID.randomUUID(),
                    journalpostId = JournalpostId("12345678"),
                    eventTid = LocalDateTime.now(),
                    status = Oppgavestatus.AAPEN,
                    aktørId = AktørId("123"),
                    aksjonspunktKoderMedStatusListe = mutableMapOf(),
                    pleietrengendeAktørId = null,
                    type = "PAPIRSØKNAD",
                    ytelse = null,
                    sendtInn = null,
                    ferdigstiltAv = null,
                    journalførtTidspunkt = null
                ))
            }
        }
    }
}

