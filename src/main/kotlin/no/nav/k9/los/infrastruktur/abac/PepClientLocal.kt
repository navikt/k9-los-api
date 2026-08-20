package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import no.nav.k9.los.infrastruktur.kontekst.Kallkontekst
import no.nav.k9.los.infrastruktur.kontekst.Områdekontekst
import no.nav.k9.los.infrastruktur.kontekst.Systemkontekst
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

class PepClientLocal : IPepClient {
    context(ctx: Brukerkontekst)
    override suspend fun erOppgaveStyrer(): Boolean {
        return true
    }

    context(ctx: Brukerkontekst)
    override suspend fun harBasisTilgang(): Boolean {
        return true
    }

    override suspend fun harBasisTilgangIEttEllerFlereOmråder(bruker: InnloggetBruker): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(bruker: InnloggetBruker): Boolean {
        return true
    }

    context(ctx: Brukerkontekst)
    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean {
        return true
    }

    context(ctx: Brukerkontekst)
    override suspend fun harTilgangTilKode6(): Boolean {
        return false
    }

    context(ctx: Brukerkontekst)
    override suspend fun harTilgangTilKode6(ident: String): Boolean {
        return false
    }

    override suspend fun erKode6Bruker(bruker: InnloggetBruker): Boolean {
        return false
    }

    context(ctx: Områdekontekst)
    override suspend fun diskresjonskoderForSak(fagsakNummer: String): Set<Diskresjonskode> {
        return setOf()
    }

    context(ctx: Områdekontekst)
    override suspend fun erSakKode6(fagsakNummer: String): Boolean {
        return false
    }

    context(ctx: Områdekontekst)
    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean {
        return false
    }

    context(ctx: Områdekontekst)
    override suspend fun diskresjonskoderForPerson(aktørId: String): Set<Diskresjonskode> {
        return setOf()
    }

    context(ctx: Områdekontekst)
    override suspend fun erAktørKode6(aktørid: String): Boolean {
        return false
    }

    context(ctx: Områdekontekst)
    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean {
        return false
    }

    context(ctx: Brukerkontekst)
    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?
    ): Boolean {
        return true
    }

    context(ctx: Systemkontekst)
    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return true
    }

}
