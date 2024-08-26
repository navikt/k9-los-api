package no.nav.k9.los.tjenester.saksbehandler.saksliste

import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import java.util.*
import kotlin.coroutines.coroutineContext

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
                    antallBehandlinger = oppgaveKø.oppgaverOgDatoer.size, //TODO dette feltet i DTO-en brukers annet sted til å sende antall inkludert reserverte, her er det ekskludert reserverte
                    antallUreserverteOppgaver = oppgaveKø.oppgaverOgDatoer.size,
                    sistEndret = oppgaveKø.sistEndret,
                    skjermet = oppgaveKø.skjermet,
                    sortering = sortering,
                    andreKriterier = oppgaveKø.filtreringAndreKriterierType,
                    kriterier = oppgaveKø.lagKriterier()
                )
            }
    }
}
