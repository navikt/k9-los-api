package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode

interface IPepClient {
    suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode>

    suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean

    // Legacy
    @Deprecated("Avhengig av coroutineContext")
    suspend fun kanLeggeUtDriftsmelding(): Boolean
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harBasisTilgang(): Boolean
    @Deprecated("Avhengig av coroutineContext")
    suspend fun erOppgaveStyrer(): Boolean
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harTilgangTilKode6(): Boolean
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harTilgangTilReserveringAvOppgaver(): Boolean
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, action: Action = Action.read): Boolean
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean
    suspend fun harBasisTilgangIEttEllerFlereOmråder(): Boolean = basisTilgangIOmråder().isNotEmpty()
    suspend fun basisTilgangIOmråder(): Set<Områder>
}
