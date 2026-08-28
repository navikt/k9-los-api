package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode

/**
 * Tilgangskontroll (PEP) for Los.
 *
 * Inneholder tilgangsvurderinger som krever utgående kall. Lokale rolle- og gruppesjekker gjøres
 * direkte på brukerkonteksten.
 *
 */
interface IPepClient {
    suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode>
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        brukerkontekst: BrukerkontekstMedOmråde,
        action: Action = Action.read,
    ) : Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action
    ) : Boolean
}
