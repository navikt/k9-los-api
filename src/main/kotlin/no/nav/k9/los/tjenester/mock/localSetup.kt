package no.nav.k9.los.tjenester.mock

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


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
}