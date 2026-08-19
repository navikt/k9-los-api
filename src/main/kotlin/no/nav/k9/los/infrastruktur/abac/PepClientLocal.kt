package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import no.nav.k9.los.infrastruktur.kontekst.Kallkontekst
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

    override suspend fun kanLeggeUtDriftsmelding(bruker: InnloggetBruker): Boolean {
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

    override suspend fun erKode6Bruker(bruker: InnloggetBruker): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Kallkontekst): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erSakKode6(fagsakNummer: String, kontekst: Kallkontekst): Boolean {
        return false
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String, kontekst: Kallkontekst): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Kallkontekst): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erAktørKode6(aktørid: String, kontekst: Kallkontekst): Boolean {
        return false
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String, kontekst: Kallkontekst): Boolean {
        return false
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
