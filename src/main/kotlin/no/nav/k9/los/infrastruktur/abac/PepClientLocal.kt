package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Områdebrukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Områdekall
import no.nav.k9.los.infrastruktur.kontekst.Områdesystemkontekst
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

class PepClientLocal : IPepClient {
    override suspend fun erOppgaveStyrer(kontekst: Områdebrukerkontekst): Boolean {
        return true
    }

    override suspend fun harBasisTilgang(kontekst: Områdebrukerkontekst): Boolean {
        return true
    }

    override suspend fun harBasisTilgangIEttEllerFlereOmråder(kontekst: Brukerkontekst): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(kontekst: Brukerkontekst): Boolean {
        return true
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(kontekst: Områdebrukerkontekst): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(kontekst: Områdebrukerkontekst): Boolean {
        return false
    }

    override suspend fun harTilgangTilKode6(ident: String, kontekst: Områdebrukerkontekst): Boolean {
        return false
    }

    override suspend fun erKode6Bruker(kontekst: Brukerkontekst): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Områdekall): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erSakKode6(fagsakNummer: String, kontekst: Områdekall): Boolean {
        return false
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String, kontekst: Områdekall): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Områdekall): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erAktørKode6(aktørid: String, kontekst: Områdekall): Boolean {
        return false
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String, kontekst: Områdekall): Boolean {
        return false
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: Områdebrukerkontekst,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?
    ): Boolean {
        return true
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: Områdesystemkontekst,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return true
    }

}
