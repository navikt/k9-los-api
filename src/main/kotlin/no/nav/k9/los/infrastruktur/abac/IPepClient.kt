package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.BrukerkontekstUtenOmråde
import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import no.nav.k9.los.infrastruktur.kontekst.Områdekontekst
import no.nav.k9.los.infrastruktur.kontekst.Systemkontekst
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

/**
 * Tilgangskontroll (PEP) for Los.
 *
 * Globale brukeroperasjoner tar [InnloggetBruker], områdeavhengige brukeroperasjoner tar
 * [Brukerkontekst], og operasjoner som også kan kjøres av system tar [MedOmråde].
 * [PepClient] velger riktig tilgangsløsning ut fra kontekstens område.
 *
 */
interface IPepClient {
    suspend fun erOppgaveStyrer(kontekst: Brukerkontekst): Boolean
    suspend fun harTilgangTilKode6(kontekst: Brukerkontekst): Boolean
    suspend fun harTilgangTilKode6(ident: String, kontekst: Brukerkontekst): Boolean

    /**
     * Er dette en kode6-konto? Global egenskap ved brukerkontoen — union over alle områder.
     * Styrer hvilken saksbehandler-rad som gjelder. Til forskjell fra [harTilgangTilKode6],
     * som besvarer om brukeren har tilgang til kode6-saker i området kallet kjører under.
     */
    suspend fun erKode6Bruker(kontekst: BrukerkontekstUtenOmråde): Boolean
    suspend fun harBasisTilgang(kontekst: Brukerkontekst): Boolean

    /**
     * Har brukeren basistilgang i minst ett område? For endepunkter som ikke er
     * områdespesifikke (f.eks. globale driftsmeldinger).
     */
    suspend fun harBasisTilgangIEttEllerFlereOmråder(bruker: InnloggetBruker): Boolean
    suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstUtenOmråde): Boolean
    suspend fun harTilgangTilReserveringAvOppgaver(kontekst: Brukerkontekst): Boolean
    suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Områdekontekst): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Områdekontekst): Set<Diskresjonskode>
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        kontekst: Brukerkontekst,
        action: Action = Action.read,
        grupperForSaksbehandler: Set<UUID>? = null
    ) : Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        kontekst: Systemkontekst,
        saksbehandler: Saksbehandler,
        action: Action
    ) : Boolean
}
