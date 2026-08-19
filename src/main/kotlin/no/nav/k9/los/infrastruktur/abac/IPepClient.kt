package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

/**
 * Tilgangskontroll (PEP) for Los.
 *
 * Alle operasjoner tar området de gjelder for, siden både rolle-/gruppeoppsett og PDP-tjeneste er
 * område-spesifikt. [PepClient] er ruting-laget som velger riktig implementasjon per område: for
 * K9 brukes sif-abac-pdp, for UNG kommer en egen løsning.
 *
 */
interface IPepClient {
    suspend fun erOppgaveStyrer(): Boolean
    suspend fun harTilgangTilKode6(): Boolean
    suspend fun harTilgangTilKode6(ident: String): Boolean

    /**
     * Er dette en kode6-konto? Global egenskap ved brukerkontoen — union over alle områder.
     * Styrer hvilken saksbehandler-rad som gjelder. Til forskjell fra [harTilgangTilKode6],
     * som besvarer om brukeren har tilgang til kode6-saker i området kallet kjører under.
     */
    suspend fun erKode6Bruker(): Boolean
    suspend fun harBasisTilgang(): Boolean

    /**
     * Har brukeren basistilgang i minst ett område? For endepunkter som ikke er
     * områdespesifikke (f.eks. globale driftsmeldinger).
     */
    suspend fun harBasisTilgangIEttEllerFlereOmråder(): Boolean
    suspend fun kanLeggeUtDriftsmelding(): Boolean
    suspend fun harTilgangTilReserveringAvOppgaver(): Boolean
    suspend fun erSakKode6(fagsakNummer: String): Boolean
    suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean
    suspend fun diskresjonskoderForSak(fagsakNummer: String): Set<Diskresjonskode>
    suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder): Set<Diskresjonskode>
    suspend fun erAktørKode6(aktørid: String): Boolean
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        action: Action = Action.read,
        grupperForSaksbehandler: Set<UUID>? = null
    ) : Boolean
    fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action
    ) : Boolean
}
