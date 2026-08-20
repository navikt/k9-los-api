package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Kallkontekst
import no.nav.k9.los.infrastruktur.kontekst.Systemkontekst
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

/**
 * Tilgangskontroll (PEP) for Los.
 *
 * Globale brukeroperasjoner tar [InnloggetBruker], områdeavhengige brukeroperasjoner tar
 * [Brukerkontekst], og operasjoner som også kan kjøres av system tar [Kallkontekst.MedOmråde].
 * [PepClient] velger riktig tilgangsløsning ut fra kontekstens område.
 *
 */
interface IPepClient {
    context(ctx: Brukerkontekst)
    suspend fun erOppgaveStyrer(): Boolean
    context(ctx: Brukerkontekst)
    suspend fun harTilgangTilKode6(): Boolean
    context(ctx: Brukerkontekst)
    suspend fun harTilgangTilKode6(ident: String): Boolean

    /**
     * Er dette en kode6-konto? Global egenskap ved brukerkontoen — union over alle områder.
     * Styrer hvilken saksbehandler-rad som gjelder. Til forskjell fra [harTilgangTilKode6],
     * som besvarer om brukeren har tilgang til kode6-saker i området kallet kjører under.
     */
    suspend fun erKode6Bruker(bruker: InnloggetBruker): Boolean
    context(ctx: Brukerkontekst)
    suspend fun harBasisTilgang(): Boolean

    /**
     * Har brukeren basistilgang i minst ett område? For endepunkter som ikke er
     * områdespesifikke (f.eks. globale driftsmeldinger).
     */
    suspend fun harBasisTilgangIEttEllerFlereOmråder(bruker: InnloggetBruker): Boolean
    suspend fun kanLeggeUtDriftsmelding(bruker: InnloggetBruker): Boolean
    context(ctx: Brukerkontekst)
    suspend fun harTilgangTilReserveringAvOppgaver(): Boolean
    context(ctx: Kallkontekst.MedOmråde)
    suspend fun erSakKode6(fagsakNummer: String): Boolean
    context(ctx: Kallkontekst.MedOmråde)
    suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean
    context(ctx: Kallkontekst.MedOmråde)
    suspend fun diskresjonskoderForSak(fagsakNummer: String): Set<Diskresjonskode>
    context(ctx: Kallkontekst.MedOmråde)
    suspend fun diskresjonskoderForPerson(aktørId: String): Set<Diskresjonskode>
    context(ctx: Kallkontekst.MedOmråde)
    suspend fun erAktørKode6(aktørid: String): Boolean
    context(ctx: Kallkontekst.MedOmråde)
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean
    context(ctx: Brukerkontekst)
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        action: Action = Action.read,
        grupperForSaksbehandler: Set<UUID>? = null
    ) : Boolean
    context(ctx: Systemkontekst)
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action
    ) : Boolean
}
