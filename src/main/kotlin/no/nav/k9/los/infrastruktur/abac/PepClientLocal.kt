package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.BrukerkontekstUtenOmråde
import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import no.nav.k9.los.infrastruktur.kontekst.Områdekontekst
import no.nav.k9.los.infrastruktur.kontekst.Systemkontekst
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

class PepClientLocal : IPepClient {
    override suspend fun erOppgaveStyrer(kontekst: Brukerkontekst): Boolean {
        return true
    }

    override suspend fun harBasisTilgang(kontekst: Brukerkontekst): Boolean {
        return true
    }

    override suspend fun harBasisTilgangIEttEllerFlereOmråder(bruker: InnloggetBruker): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstUtenOmråde): Boolean {
        return true
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(kontekst: Brukerkontekst): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(kontekst: Brukerkontekst): Boolean {
        return false
    }

    override suspend fun harTilgangTilKode6(ident: String, kontekst: Brukerkontekst): Boolean {
        return false
    }

    override suspend fun erKode6Bruker(kontekst: BrukerkontekstUtenOmråde): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Områdekontekst): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Områdekontekst): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: Brukerkontekst,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?
    ): Boolean {
        return true
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: Systemkontekst,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return true
    }

}
