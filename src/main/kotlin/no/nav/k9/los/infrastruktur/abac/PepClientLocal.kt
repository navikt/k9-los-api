package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.kontekst.BrukerkontekstUtenOmråde
import no.nav.k9.los.infrastruktur.kontekst.Systemkontekst
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

class PepClientLocal : IPepClient {
    override suspend fun erOppgaveStyrer(kontekst: BrukerkontekstMedOmråde): Boolean {
        return true
    }

    override suspend fun harBasisTilgang(kontekst: BrukerkontekstMedOmråde): Boolean {
        return true
    }

    override suspend fun harBasisTilgangIEttEllerFlereOmråder(kontekst: BrukerkontekstUtenOmråde): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstUtenOmråde): Boolean {
        return true
    }

    override suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstMedOmråde): Boolean {
        return true
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(kontekst: BrukerkontekstMedOmråde): Boolean {
        return true
    }

    override suspend fun harTilgangTilKode6(kontekst: BrukerkontekstMedOmråde): Boolean {
        return false
    }

    override suspend fun harTilgangTilKode6(ident: String, kontekst: BrukerkontekstMedOmråde): Boolean {
        return false
    }

    override suspend fun harKode6TilgangIEttEllerFlereOmråder(kontekst: BrukerkontekstUtenOmråde): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Systemkontekst): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Systemkontekst): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: BrukerkontekstMedOmråde,
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
