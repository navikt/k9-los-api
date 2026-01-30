package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

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

    override suspend fun diskresjonskoderForSak(fagsakNummer: String): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erSakKode6(fagsakNummer: String): Boolean {
        return false
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erAktørKode6(aktørid: String): Boolean {
        return false
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean {
        return false
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?
    ): Boolean {
        return true
    }

    override fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return true
    }

}
