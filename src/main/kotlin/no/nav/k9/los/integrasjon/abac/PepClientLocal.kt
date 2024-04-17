package no.nav.k9.los.integrasjon.abac

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Saksbehandler

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

    override suspend fun harTilgangTilLesSak(fagsakNummer: String, aktørid: String): Boolean {
        return true
    }

    override fun harTilgangTilLesSak(fagsakNummer: String, aktørid: String, bruker: Saksbehandler): Boolean {
        return true
    }

    override suspend fun harTilgangTilReservingAvOppgaver(): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(): Boolean {
        return false
    }

    override fun harTilgangTilKode6(ident: String): Boolean {
        return false
    }

    override suspend fun kanSendeSakTilStatistikk(fagsakNummer: String): Boolean {
        return true
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

    override suspend fun harTilgangTilOppgave(oppgave: Oppgave): Boolean {
        return true
    }

    override fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        bruker: Saksbehandler
    ): Boolean {
        return true
    }

    override suspend fun harTilgangTilÅReservereOppgave(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        bruker: Saksbehandler
    ): Boolean {
        return true
    }

}
