package no.nav.k9.los.infrastruktur.abac

import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.UUID

/**
 * Skjelett: tilgangsklient for området UNG.
 *
 * Erstatter [SifAbacPdpKlientK9] for UNG-oppgaver. Hvilken PDP-tjeneste UNG skal bruke er ikke
 * bestemt ennå, så alle operasjoner kaster [NotImplementedError] inntil videre.
 *
 * Klienten implementerer [ISifAbacPdpKlient] for å kunne rutes til fra [PepClient]. Når UNG-
 * varianten er på plass bør interfacet få et område-nøytralt navn.
 */
class SifAbacPdpKlientUng : ISifAbacPdpKlient {

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> =
        ikkeImplementert("diskresjonskoderPerson")

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> =
        ikkeImplementert("diskresjonskoderSak")

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto): Boolean =
        ikkeImplementert("harTilgangTilSak")

    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>): Boolean =
        ikkeImplementert("harTilgangTilPersoner")

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean = ikkeImplementert("harTilgangTilSak(grupper)")

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean = ikkeImplementert("harTilgangTilPersoner(grupper)")

    private fun ikkeImplementert(operasjon: String): Nothing =
        throw NotImplementedError("Tilgangskontroll for område UNG er ikke implementert ennå ($operasjon)")
}

