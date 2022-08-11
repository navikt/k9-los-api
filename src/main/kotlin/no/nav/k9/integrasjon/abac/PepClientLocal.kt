package no.nav.k9.integrasjon.abac

import no.nav.k9.domene.lager.oppgave.Oppgave

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

    override suspend fun harTilgangTilReservingAvOppgaver(): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(): Boolean {
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

}
