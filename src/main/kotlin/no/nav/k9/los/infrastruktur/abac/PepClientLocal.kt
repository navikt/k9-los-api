package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode

class PepClientLocal : IPepClient {
    override suspend fun harSaksbehandlerTilgangTilKode6(ident: String, brukerkontekst: BrukerkontekstMedOmråde): Boolean {
        return false
    }

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode> {
        return setOf()
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        brukerkontekst: BrukerkontekstMedOmråde,
        action: Action,
    ): Boolean {
        return true
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return true
    }

}
