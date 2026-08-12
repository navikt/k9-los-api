package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

class PepClientLocal : IPepClient {
    override suspend fun erOppgaveStyrer(område: Områder): Boolean {
        return true
    }

    override suspend fun harBasisTilgang(område: Områder): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(område: Områder): Boolean {
        return true
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(område: Områder): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(område: Områder): Boolean {
        return false
    }

    override suspend fun harTilgangTilKode6(ident: String, område: Områder): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erSakKode6(fagsakNummer: String, område: Områder): Boolean {
        return false
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String, område: Områder): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun erAktørKode6(aktørid: String, område: Områder): Boolean {
        return false
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String, område: Områder): Boolean {
        return false
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?,
        område: Områder
    ): Boolean {
        return true
    }

    override fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        område: Områder
    ): Boolean {
        return true
    }

}
