package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstUtenOmråde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

/**
 * Tilgangskontroll (PEP) for Los.
 *
 * Globale brukeroperasjoner tar [BrukerkontekstUtenOmråde], områdeavhengige brukeroperasjoner tar
 * [BrukerkontekstMedOmråde].
 * [PepClient] velger riktig tilgangsløsning ut fra kontekstens område.
 *
 */
interface IPepClient {
    suspend fun erOppgaveStyrer(kontekst: BrukerkontekstMedOmråde): Boolean
    suspend fun harTilgangTilKode6(kontekst: BrukerkontekstMedOmråde): Boolean
    suspend fun harTilgangTilKode6(ident: String, kontekst: BrukerkontekstMedOmråde): Boolean
    suspend fun harKode6TilgangIEttEllerFlereOmråder(kontekst: BrukerkontekstUtenOmråde): Boolean
    suspend fun harBasisTilgang(kontekst: BrukerkontekstMedOmråde): Boolean
    suspend fun harBasisTilgangIEttEllerFlereOmråder(kontekst: BrukerkontekstUtenOmråde): Boolean
    suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstUtenOmråde): Boolean

    /**
     * Advarsel: driftsrolle er uavhengig av område
     */
    suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstMedOmråde): Boolean
    suspend fun harTilgangTilReserveringAvOppgaver(kontekst: BrukerkontekstMedOmråde): Boolean
    suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode>
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        kontekst: BrukerkontekstMedOmråde,
        action: Action = Action.read,
        grupperForSaksbehandler: Set<UUID>? = null
    ) : Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        område: Områder,
        saksbehandler: Saksbehandler,
        action: Action
    ) : Boolean
}
