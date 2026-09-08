package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode

class PepClientLocal : IPepClient {
    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> = emptySet()
    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> = emptySet()

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        brukerkontekst: BrukerkontekstMedOmråde,
        action: Action,
    ): Boolean {
        brukerkontekst.krevOmråde(oppgave.oppgavetype.område.tilOmråderEnum())
        return true
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action,
    ): Boolean {
        require(område == oppgave.oppgavetype.område.tilOmråderEnum()) { "Oppgaven tilhører et annet område" }
        return true
    }

    // Legacy
    override suspend fun kanLeggeUtDriftsmelding(): Boolean = true
    override suspend fun harBasisTilgang(): Boolean = true
    override suspend fun erOppgaveStyrer(): Boolean = true
    override suspend fun harTilgangTilKode6(): Boolean = true
    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean = true
    override suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, action: Action): Boolean = true
    override suspend fun harTilgangTilOppgaveV3(oppgave: Oppgave, saksbehandler: Saksbehandler, action: Action): Boolean = true
}
