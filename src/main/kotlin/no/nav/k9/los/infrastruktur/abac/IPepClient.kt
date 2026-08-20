package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Områdebrukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Områdekall
import no.nav.k9.los.infrastruktur.kontekst.Områdesystemkontekst
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

/**
 * Tilgangskontroll (PEP) for Los.
 *
 * Globale brukeroperasjoner tar [Brukerkontekst], områdeavhengige brukeroperasjoner tar
 * [Områdebrukerkontekst], og operasjoner som også kan kjøres av system tar [Områdekall].
 * [PepClient] velger riktig tilgangsløsning ut fra kontekstens område.
 *
 */
interface IPepClient {
    suspend fun erOppgaveStyrer(kontekst: Områdebrukerkontekst): Boolean
    suspend fun harTilgangTilKode6(kontekst: Områdebrukerkontekst): Boolean
    suspend fun harTilgangTilKode6(ident: String, kontekst: Områdebrukerkontekst): Boolean

    /**
     * Er dette en kode6-konto? Global egenskap ved brukerkontoen — union over alle områder.
     * Styrer hvilken saksbehandler-rad som gjelder. Til forskjell fra [harTilgangTilKode6],
     * som besvarer om brukeren har tilgang til kode6-saker i området kallet kjører under.
     */
    suspend fun erKode6Bruker(kontekst: Brukerkontekst): Boolean
    suspend fun harBasisTilgang(kontekst: Områdebrukerkontekst): Boolean

    /**
     * Har brukeren basistilgang i minst ett område? For endepunkter som ikke er
     * områdespesifikke (f.eks. globale driftsmeldinger).
     */
    suspend fun harBasisTilgangIEttEllerFlereOmråder(kontekst: Brukerkontekst): Boolean
    suspend fun kanLeggeUtDriftsmelding(kontekst: Brukerkontekst): Boolean
    suspend fun harTilgangTilReserveringAvOppgaver(kontekst: Områdebrukerkontekst): Boolean
    suspend fun erSakKode6(fagsakNummer: String, kontekst: Områdekall): Boolean
    suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String, kontekst: Områdekall): Boolean
    suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Områdekall): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Områdekall): Set<Diskresjonskode>
    suspend fun erAktørKode6(aktørid: String, kontekst: Områdekall): Boolean
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String, kontekst: Områdekall): Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        kontekst: Områdebrukerkontekst,
        action: Action = Action.read,
        grupperForSaksbehandler: Set<UUID>? = null
    ) : Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        kontekst: Områdesystemkontekst,
        saksbehandler: Saksbehandler,
        action: Action
    ) : Boolean
}
