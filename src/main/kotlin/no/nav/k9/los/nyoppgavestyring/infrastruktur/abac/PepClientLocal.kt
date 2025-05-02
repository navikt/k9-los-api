package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler

class PepClientLocal : IPepClient {
    override suspend fun erOppgaveStyrer(): Boolean {
        return true
    }

    override suspend fun harBasisTilgang(): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(): Boolean {
        return true
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(): Boolean {
        return false
    }

    override suspend fun harTilgangTilKode6(ident: String): Boolean {
        return false
    }

    override suspend fun erSakKode6(fagsakNummer: String): Boolean {
        return false
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean {
        return false
    }

    override suspend fun erAktørKode6(aktørid: String): Boolean {
        return false
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean {
        return false
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        action: Action,
        auditlogging: Auditlogging
    ): Boolean {
        return true
    }

    override fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        auditlogging: Auditlogging
    ): Boolean {
        return true
    }

}
