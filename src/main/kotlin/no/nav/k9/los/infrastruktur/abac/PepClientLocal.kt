package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode

class PepClientLocal : IPepClient {
    private val tilganger = Tilganger(
        basis = true,
        kode6 = false,
        oppgavestyring = true,
        reservering = true,
        drift = true
    )

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> = emptySet()
    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> = emptySet()

    override suspend fun tilganger(område: Områder): Tilganger = tilganger
    override suspend fun kanLeggeUtDriftsmelding(): Boolean = tilganger.drift
    override suspend fun harBasisTilgang(): Boolean = tilganger.basis
    override suspend fun erOppgaveStyrer(): Boolean = tilganger.oppgavestyring
    override suspend fun harTilgangTilKode6(): Boolean = tilganger.kode6
    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean = tilganger.reservering
    override suspend fun basisTilgangIOmråder(): Set<Områder> = Områder.entries.toSet()

    override suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, action: Action): Boolean = true
    override suspend fun harTilgangTilOppgaveV3(område: Områder, idToken: IIdToken, oppgave: Oppgave, action: Action): Boolean = true

    override suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, saksbehandler: Saksbehandler, action: Action): Boolean = true
    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean {
        require(område == oppgave.oppgavetype.område.tilOmråderEnum()) { "Oppgaven tilhører et annet område" }
        return true
    }
}
