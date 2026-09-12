package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode

interface IPepClient {
    // For pep-cache
    suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode>

    // Tilgangsflagg
    suspend fun kanLeggeUtDriftsmelding(): Boolean
    suspend fun harBasisTilgang(): Boolean
    suspend fun erOppgaveStyrer(): Boolean
    suspend fun harTilgangTilKode6(): Boolean
    suspend fun harTilgangTilReserveringAvOppgaver(): Boolean
    suspend fun harBasisTilgangIEttEllerFlereOmråder(): Boolean = basisTilgangIOmråder().isNotEmpty()
    suspend fun basisTilgangIOmråder(): Set<Områder>

    // Tilgang til oppgave, for innlogget bruker
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, action: Action = Action.read): Boolean
    suspend fun harTilgangTilOppgaveV3(område: Områder, idToken: IIdToken, oppgave: Oppgave, action: Action): Boolean

    // Tilgang til oppgave, for en annen saksbehandler
    @Deprecated("Avhengig av coroutineContext")
    suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean
}
