package no.nav.k9.tjenester.saksbehandler.saksliste

import kotlin.coroutines.coroutineContext
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.integrasjon.rest.idToken
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import java.util.*

class SakslisteTjeneste(
    private val oppgaveTjeneste: OppgaveTjeneste

) {
    suspend fun hentSaksbehandlersKøer(): List<OppgavekøDto> {
        val hentOppgaveKøer = oppgaveTjeneste.hentOppgaveKøer()
        return hentOppgaveKøer
            .filter { oppgaveKø ->
                oppgaveKø.saksbehandlere
                    .any { saksbehandler ->
                        saksbehandler.epost.lowercase(Locale.getDefault()) == coroutineContext.idToken().getUsername()
                            .lowercase(Locale.getDefault())
                    }
            }
            .map { oppgaveKø ->
                val sortering = SorteringDto(oppgaveKø.sortering, oppgaveKø.fomDato, oppgaveKø.tomDato)

                OppgavekøDto(
                    id = oppgaveKø.id,
                    navn = oppgaveKø.navn,
                    behandlingTyper = oppgaveKø.filtreringBehandlingTyper,
                    fagsakYtelseTyper = oppgaveKø.filtreringYtelseTyper,
                    saksbehandlere = oppgaveKø.saksbehandlere,
                    antallBehandlinger = oppgaveKø.oppgaverOgDatoer.size,
                    sistEndret = oppgaveKø.sistEndret,
                    skjermet = oppgaveKø.skjermet,
                    sortering = sortering,
                    andreKriterier = oppgaveKø.filtreringAndreKriterierType
                )

            }
    }
}
