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
 * Området har inntil videre default [Områder.K9] slik at eksisterende kallsteder (som p.t. kun
 * håndterer K9) er uendret. Defaulten skal fjernes når kallerne er område-bevisste.
 */
interface IPepClient {
    suspend fun erOppgaveStyrer(område: Områder = Områder.K9): Boolean
    suspend fun harTilgangTilKode6(område: Områder = Områder.K9): Boolean
    suspend fun harTilgangTilKode6(ident: String, område: Områder = Områder.K9): Boolean
    suspend fun harBasisTilgang(område: Områder = Områder.K9): Boolean
    suspend fun kanLeggeUtDriftsmelding(område: Områder = Områder.K9): Boolean
    suspend fun harTilgangTilReserveringAvOppgaver(område: Områder = Områder.K9): Boolean
    suspend fun erSakKode6(fagsakNummer: String, område: Områder = Områder.K9): Boolean
    suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String, område: Områder = Områder.K9): Boolean
    suspend fun diskresjonskoderForSak(fagsakNummer: String, område: Områder = Områder.K9): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String, område: Områder = Områder.K9): Set<Diskresjonskode>
    suspend fun erAktørKode6(aktørid: String, område: Områder = Områder.K9): Boolean
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String, område: Områder = Områder.K9): Boolean
    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        action: Action = Action.read,
        grupperForSaksbehandler: Set<UUID>? = null,
        område: Områder = Områder.K9
    ) : Boolean
    fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.oppgaveuthenting.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        område: Områder = Områder.K9
    ) : Boolean
}